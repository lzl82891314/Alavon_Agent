# 12 - GameCoordinator 与动作收集规范

## 1. 文档级别

本文是游戏运行编排和动作收集的规范性设计。实现如果不满足本文，不能宣称具备正确的多人阿瓦隆运行闭环。

本文解决：

- 谁掌控游戏流程。
- 如何知道当前轮次、阶段和队长。
- 如何请求单个或多个玩家行动。
- 如何收集组队投票和秘密任务票。
- 如何保证同一批次玩家看不到其他人的未公开选择。
- 如何处理 LLM、Human、Scripted 和 Remote 玩家。
- 如何在超时、崩溃和重试后恢复。
- 如何判断游戏进入下一阶段或最终结束。

## 2. 核心结论

`GameCoordinator` 必须是确定性的 Java 应用服务，不能由 LLM 控制。

```text
LLM / Human / Scripted Player
          |
          v
     ProposedAction
          |
          v
GameCoordinator ---------> ActionCollector
          |                      |
          v                      v
  GameRuleEngine <--------- CompletedBatch
          |
          v
     Domain Events
          |
          v
   Game Aggregate
          |
          v
 EventStore / SnapshotStore
```

LLM 只决定玩家行为。轮次、阶段、投票统计、秘密信息、状态转换和胜负由可信代码决定。

## 3. 组件职责

### 3.1 GameCoordinator

负责：

- 创建和开始游戏。
- 加载权威 Game Aggregate。
- 请求 `GameRuleEngine` 给出下一需求。
- 创建单人或多人 Action Batch。
- 解析对应 Player Controller。
- 调度 LLM、Human、Scripted 或 Remote 行动。
- 将候选动作交给 ActionCollector。
- 收齐后把命令提交给 GameRuleEngine。
- 原子追加 Domain Events。
- 保存快照并发布过滤后的事件。
- 在暂停、等待、终局或预算耗尽时停止自动推进。

不负责：

- 自己统计规则结果。
- 自己决定动作是否合法。
- 自己决定三次任务成功后进入什么阶段。
- 修改 Agent Belief 或 Strategy。
- 生成玩家发言。
- 把先提交的秘密选择泄露给后提交者。

`GameCoordinator` 可以编排 `CognitionCommitService`，但不能直接修改 Belief 或 Strategy。该服务只在领域批次已经被规则引擎接受后，按每个 Agent Run 的私有作用域提交正式认知状态。

### 3.2 Game Aggregate

负责保存权威状态并应用 Domain Event。

### 3.3 GameRuleEngine

负责：

- 根据 Game State 生成 Next Requirement。
- 校验单人动作或 Completed Action Batch。
- 产生 Domain Events。
- 决定自动状态转换和终局结果。

### 3.4 ActionCollector

负责：

- 创建动作批次。
- 接受独立玩家提交。
- 校验批次、玩家和幂等键。
- 跟踪缺失玩家。
- 判断批次是否完成、过期或失效。
- 向 Coordinator 返回 Completed Batch。

ActionCollector 不判断投票是否通过，也不判断任务是否成功。

### 3.5 PlayerController

负责产生某个玩家的 `ProposedAction`。它不能访问 Game Repository 或直接写 Action Batch。

## 4. 权威 Game State

```java
public record GameState(
        GameId gameId,
        long version,
        GameStatus status,
        GamePhase phase,
        int roundNo,
        int proposalAttempt,
        int currentLeaderSeat,
        List<GamePlayer> players,
        List<PlayerId> proposedTeam,
        Optional<ActionBatchId> activeBatchId,
        int successfulMissionCount,
        int failedMissionCount,
        int consecutiveRejectedTeams,
        List<MissionResult> missionHistory,
        Optional<Camp> winner,
        Optional<GameEndReason> endReason
) {}
```

个人投票和任务票保存在 Action Batch 及权威事件中。聚合可以缓存当前批次摘要，但不向普通公共视图暴露秘密提交。

状态不变量：

- `roundNo` 只由领域事件推进。
- 同一游戏最多存在一个影响领域推进的 Active Batch。
- `activeBatchId` 必须与当前 Phase 匹配。
- `ENDED` 后不得创建新 Batch。
- Winner 只能在 `ENDED` 状态存在。
- 任务历史一旦产生不可修改。

## 5. GameCoordinator 接口

```java
public interface GameCoordinator {
    CreateGameResult create(CreateGameCommand command);
    StartGameResult start(StartGameCommand command);
    AdvanceResult advance(AdvanceGameCommand command);
    RunResult runUntilBlocked(RunGameCommand command);
    SubmitActionResult submit(SubmitPlayerActionCommand command);
    RetryTurnResult retry(RetryTurnCommand command);
    ResumeGameResult resume(ResumeGameCommand command);
    CancelGameResult cancel(CancelGameCommand command);
}
```

`GameCommandService` 是交付层使用的应用门面，可以直接由 `DefaultGameCoordinator` 实现，或者委托给它。项目中不允许同时存在两套独立推进逻辑。

## 6. NextRequirement 类型体系

```java
public sealed interface NextRequirement permits
        AutomaticTransitionRequirement,
        SinglePlayerActionRequirement,
        ParallelPlayerActionRequirement,
        ExternalPlayerActionRequirement,
        TerminalRequirement {
    GameId gameId();
    long sourceGameVersion();
    GamePhase phase();
}
```

### 6.1 AutomaticTransitionRequirement

用于无需玩家选择的转换：

- 开局角色分配完成后进入讨论。
- 收齐任务票后解析任务结果。
- 回合结算后进入下一轮。

### 6.2 SinglePlayerActionRequirement

```java
public record SinglePlayerActionRequirement(
        GameId gameId,
        long sourceGameVersion,
        GamePhase phase,
        PlayerId playerId,
        ActionType actionType,
        AllowedActionSet allowedActions,
        VisibilitySnapshotRef viewSnapshot,
        Instant deadline
) implements NextRequirement {}
```

适用：

- 公开发言。
- 回应质疑。
- 队长提案。
- 队长总结。
- 刺客选择目标。

### 6.3 ParallelPlayerActionRequirement

```java
public record ParallelPlayerActionRequirement(
        GameId gameId,
        long sourceGameVersion,
        GamePhase phase,
        ActionType actionType,
        Set<PlayerId> requiredPlayers,
        AllowedActionSet allowedActions,
        VisibilitySnapshotRef sharedPublicSnapshot,
        DisclosurePolicy disclosurePolicy,
        Instant deadline
) implements NextRequirement {}
```

适用：

- 所有玩家的组队投票。
- 入选任务队员的任务选择。
- 未来扩展中的同时秘密选择。

### 6.4 ExternalPlayerActionRequirement

用于 Human 或异步 Remote Controller。它可以是 Single 或 Parallel Requirement 的一个参与者，但需要持久化等待外部输入。

### 6.5 TerminalRequirement

表示游戏已经结束，不再允许新动作。

## 7. Action Batch

```java
public record ActionBatch(
        ActionBatchId batchId,
        GameId gameId,
        long sourceGameVersion,
        TurnToken turnToken,
        GamePhase phase,
        ActionType actionType,
        Set<PlayerId> requiredPlayers,
        Map<PlayerId, ActionSubmission> submissions,
        VisibilitySnapshotRef inputSnapshot,
        DisclosurePolicy disclosurePolicy,
        ActionBatchStatus status,
        Instant createdAt,
        Instant deadline,
        long batchVersion
) {}
```

### 7.1 ActionBatchStatus

```text
OPEN
PARTIALLY_COLLECTED
COMPLETED
EXPIRED
CANCELLED
INVALIDATED
COMMITTED
```

状态转换：

```text
OPEN
  -> PARTIALLY_COLLECTED
  -> COMPLETED
  -> COMMITTED

OPEN / PARTIALLY_COLLECTED
  -> EXPIRED
  -> CANCELLED
  -> INVALIDATED
```

`COMPLETED` 表示已收齐但尚未产生领域事件；`COMMITTED` 表示 GameRuleEngine 已接受且事件已原子保存。

### 7.2 ActionSubmission

```java
public record ActionSubmission(
        ActionBatchId batchId,
        PlayerId playerId,
        ProposedAction action,
        String idempotencyKey,
        long expectedBatchVersion,
        String controllerExecutionId,
        Instant submittedAt
) {}
```

对 LLM Controller，`controllerExecutionId` 必须引用一个带
`gameId + ownerPlayerId + agentInstanceId` 的 `AgentRun`。客户端、Human 或 Remote
Controller 不能伪造其他玩家的 execution ID；服务端根据当前 Batch 的 `playerId` 解析并校验归属。

### 7.3 CompletedActionBatch

```java
public record CompletedActionBatch(
        ActionBatchId batchId,
        GameId gameId,
        long sourceGameVersion,
        GamePhase phase,
        ActionType actionType,
        Map<PlayerId, ProposedAction> actions,
        DisclosurePolicy disclosurePolicy
) {}
```

## 8. ActionCollector 接口

```java
public interface ActionCollector {
    ActionBatch open(ParallelPlayerActionRequirement requirement, TurnToken turnToken);
    ActionBatch open(SinglePlayerActionRequirement requirement, TurnToken turnToken);
    SubmissionResult submit(ActionSubmission submission);
    Optional<ActionBatch> findActive(GameId gameId);
    Optional<CompletedActionBatch> completed(ActionBatchId batchId);
    ActionBatch expire(ActionBatchId batchId, Instant now);
    ActionBatch invalidate(ActionBatchId batchId, String reason);
    void markCommitted(ActionBatchId batchId, long committedGameVersion);
}
```

ActionCollector 的持久化实现必须使用乐观并发控制，不能仅保存在内存 Map。

## 9. 逻辑并行原则

“并行”表示所有参与者基于同一个已冻结输入状态独立决策，不要求 HTTP 请求在同一纳秒发生。

必须保证：

1. 所有参与者使用相同 `sourceGameVersion`。
2. 所有参与者使用同一个公共 `VisibilitySnapshotRef`。
3. 每个参与者仍使用自己的 Player Private View。
4. 某个参与者不能观察本 Batch 中其他人的 Submission。
5. Batch 完成前不产生公开投票事件。
6. Batch 完成后由 GameRuleEngine 一次性裁定。

LLM 调用可以顺序执行以控制并发，也可以并行执行以降低延迟，只要输入快照和披露规则相同。

## 10. GameCoordinator 推进算法

```java
public AdvanceResult advance(AdvanceGameCommand command) {
    Game game = gameRepository.require(command.gameId());

    if (game.status().isTerminal()) {
        return AdvanceResult.terminal(game.view());
    }

    Optional<ActionBatch> active = actionCollector.findActive(game.id());
    if (active.isPresent()) {
        return continueActiveBatch(game, active.get(), command.runBudget());
    }

    NextRequirement requirement = ruleEngine.nextRequirement(game.state());
    return handleRequirement(game, requirement, command.runBudget());
}
```

### 10.1 handleRequirement

```text
AutomaticTransition
  -> ruleEngine.decide
  -> append events
  -> return canAutoContinue=true

SinglePlayerAction
  -> open single ActionBatch
  -> invoke or wait for controller
  -> collect action
  -> commit completed batch

ParallelPlayerAction
  -> open parallel ActionBatch
  -> invoke all required controllers against frozen snapshot
  -> collect independently
  -> commit only after complete

Terminal
  -> ensure active batch absent
  -> return terminal result
```

## 11. 事务边界

模型调用不得位于数据库事务中。

### Transaction A：打开批次

```text
load game
verify no active batch
obtain NextRequirement
persist ActionBatch
append ActionBatchOpened application audit
commit
```

### Transaction-free：执行 Controller

```text
load frozen view
invoke LLM / Human / Scripted / Remote controller
receive ProposedAction
```

LLM 在这里创建并持久化私有 `AgentRun`、Tool Call 和 Belief/Strategy Draft。Run 只能使用该参与者自己的
`PlayerPrivateView`；并行 Batch 中已经到达的其他 Submission 不是这个视图的一部分。

### Transaction B：提交单个动作

```text
load ActionBatch
verify batch version, player and idempotency key
persist submission
update batch status
commit
```

### Transaction C：完成并提交领域

```text
load completed batch
load game at source version
ruleEngine decides
append domain events using expected game version
mark batch COMMITTED
for each accepted LLM submission:
  verify AgentRun belongs to this batch/player/source view
  commit formal PlayerCognitionState from its private Draft
  create that player's Cognition Checkpoint
save snapshot when required
commit atomically
```

如果 Game Version 已变化，Batch 必须 `INVALIDATED`，不能把旧动作应用到新状态。
所有关联的 Agent Run 标记为 `SUPERSEDED`，其私有 Draft 不得覆盖后续回合的正式认知状态。批次未完成、过期或取消时同样不能创建正式 Checkpoint。

## 12. Player Controller 调度

```java
public interface ControllerDispatcher {
    ControllerDispatchResult dispatch(
            ActionBatch batch,
            PlayerActionRequirement participant
    );
}
```

| Controller | 调度行为 |
| --- | --- |
| `LLM` | 创建 Agent Execution，可同步等待或异步回调 |
| `HUMAN` | 创建 Pending Human Action，返回 WAITING |
| `SCRIPTED` | 立即返回确定性 ProposedAction |
| `REMOTE` | 创建远程请求并等待回调 |

同一 Parallel Batch 可以混合多种 Controller。

## 13. 组队投票流程

### 13.1 输入

所有玩家收到：

- 相同的当前轮次和提案队伍。
- 相同的公开时间线截止 Sequence。
- 各自不同的私有视图、Belief 和 Strategy。
- 只允许 `TEAM_VOTE`。

### 13.2 输出

```json
{
  "action": {
    "type": "TEAM_VOTE",
    "choice": "APPROVE"
  }
}
```

### 13.3 收集和披露

```text
TeamVoteBatchOpened             ADMIN_ONLY
TeamVoteSubmissionReceived     PLAYER_PRIVATE + ADMIN_ONLY
TeamVoteBatchCompleted          ADMIN_ONLY
TeamVotesRevealed               PUBLIC
TeamApproved / TeamRejected     PUBLIC
```

`TeamVotesRevealed` 一次性包含全部玩家公开投票。提交顺序不属于公共游戏信息。

### 13.4 裁定

GameRuleEngine 根据 Rule Pack 的多数规则决定通过或否决。ActionCollector 不统计最终规则结果。

## 14. 任务行动流程

### 14.1 参与者

只有当前提案队伍成员进入 Batch。

### 14.2 输入隔离

每名参与者不能看到其他任务成员在本 Batch 中的选择。

### 14.3 输出

```json
{
  "action": {
    "type": "MISSION_CHOICE",
    "choice": "SUCCESS"
  }
}
```

好人角色提交 `FAIL` 时由 GameRuleEngine 拒绝。Harness 可以预校验，但不能替代权威校验。

### 14.4 披露

```text
MissionActionBatchOpened        ADMIN_ONLY
MissionChoiceSubmitted          PLAYER_PRIVATE + ADMIN_ONLY
MissionActionBatchCompleted     ADMIN_ONLY
MissionSucceeded / Failed       PUBLIC
```

公共 Mission Result 可以包含失败票数量，但绝不包含玩家与任务票的对应关系。

## 15. 单人动作流程

适用发言、提案、回应和刺杀。虽然只有一个参与者，仍建议使用 Single Action Batch，以统一：

- Turn Token。
- 幂等性。
- 超时。
- Controller Execution。
- 崩溃恢复。

单人公开动作可以在领域接受后立即公开，不需要等待其他玩家。

## 16. 动作提交通道

### 16.1 认知工具与游戏动作分离

认知工具：

- 可选调用零次或多次。
- 默认只读。
- 返回分析所需证据。

游戏动作：

- 当前 Requirement 必须且只能提交一次。
- 最终转换为 `ProposedAction`。
- 只能由 GameCoordinator 交给规则引擎。

### 16.2 ActionSubmissionMode

```java
public enum ActionSubmissionMode {
    STRUCTURED_OUTPUT,
    FUNCTION_CALL
}
```

默认 `STRUCTURED_OUTPUT`。

### 16.3 Structured Output

模型完成认知工具循环后返回阶段专用 Schema。Harness 解码为 ProposedAction。

### 16.4 Function Call

可以把最终动作映射为严格 Function Tool，例如 `submit_team_vote`。但函数执行器只能构造 ProposedAction，绝不能直接写 Game 或 Action Batch。

禁止同时要求模型通过 Structured Output 和 Function Call 重复提交同一个动作。

## 17. 超时与失败策略

```java
public record ActionCollectionPolicy(
        Duration batchDeadline,
        int controllerRetryAttempts,
        MissingSubmissionPolicy missingSubmissionPolicy,
        boolean preserveCompletedSubmissions,
        boolean allowHumanTakeover,
        Optional<String> fallbackAgentProfileId
) {}
```

`MissingSubmissionPolicy`：

```text
PAUSE_GAME
RETRY_MISSING_ONLY
USE_CONFIGURED_FALLBACK
ALLOW_HUMAN_TAKEOVER
CANCEL_GAME
```

默认策略：

- 只重试缺失或失败的参与者。
- 已验证提交保持不变。
- 不让后续重试看到已提交内容。
- 重试耗尽后暂停游戏。
- 不自动替玩家选择 APPROVE、REJECT、SUCCESS 或 FAIL。

## 18. 混合 Controller Batch

例如五人组队投票包含三个 LLM、一个 Human 和一个 Scripted：

```text
open batch
-> Scripted immediately submits
-> dispatch three LLM executions
-> expose Pending Action to Human
-> collect four available submissions privately
-> wait for Human
-> Human submits
-> complete batch
-> reveal all votes together
```

LLM 已完成的投票不能因为等待 Human 而公开。

## 19. 幂等性与并发

### 19.1 Submission 幂等

- 相同 `batchId + playerId + idempotencyKey`、相同 Payload：返回原结果。
- 相同 Key、不同 Payload：返回冲突。
- 同一玩家用新 Key 再次提交：如果已有有效 Submission，拒绝重复。

### 19.2 Batch 并发

- 使用 `batchVersion` 乐观锁。
- 两个 Submission 可以并发写入，但必须合并而不丢失。
- Completed 到 Committed 只能执行一次。

### 19.3 Game 并发

- Batch 记录 `sourceGameVersion`。
- 领域提交使用该版本作为 `expectedVersion`。
- 不匹配时失效整个 Batch，并记录冲突原因。

## 20. 崩溃恢复

### 20.1 恢复算法

```text
load Game Aggregate
-> load Active ActionBatch
-> compare game version and batch source version
-> if mismatch: invalidate batch
-> if batch completed but not committed: retry domain commit
-> if batch partially collected: dispatch missing participants only
-> if batch expired: apply ActionCollectionPolicy
-> if no batch: ask RuleEngine for NextRequirement
```

### 20.2 Provider 已响应但尚未提交

Agent Execution 保存结果和 `controllerExecutionId`。恢复时优先重交同一 ProposedAction，而不是再次调用模型。

### 20.3 无法确定 Provider 是否完成

如果没有持久结果：

- 将旧 Execution 标记为 `INTERRUPTED`。
- 创建新 Execution Attempt。
- 使用相同冻结输入快照。
- 通过 Submission 幂等性防止重复计票。

## 21. 终局转换

经典规则包至少定义：

| 条件 | 领域事件 | 最终状态 |
| --- | --- | --- |
| 邪恶完成 3 次失败任务 | `GameEnded(EVIL, THREE_FAILED_MISSIONS)` | `ENDED` |
| 连续 5 次组队否决 | `GameEnded(EVIL, FIVE_REJECTED_TEAMS)` | `ENDED` |
| 好人完成 3 次成功任务 | `AssassinationOpened` | `RUNNING/ASSASSINATION` |
| 刺客命中梅林 | `GameEnded(EVIL, MERLIN_ASSASSINATED)` | `ENDED` |
| 刺客未命中梅林 | `GameEnded(GOOD, ASSASSINATION_MISSED)` | `ENDED` |
| 管理员取消 | `GameCancelled` | `CANCELLED` |

终局处理必须：

- 拒绝所有旧 Turn Token。
- 取消或失效未提交 Batch。
- 停止新 Controller Dispatch。
- 保存终局快照。
- 发布公共结束事件。
- 保留审计和回放数据。

## 22. 事件与可见性矩阵

| 事件 | Public | Player Private | Admin |
| --- | --- | --- | --- |
| `ActionBatchOpened` | 否 | 仅参与者知道需要行动 | 是 |
| `ActionSubmissionReceived` | 否 | 仅提交者确认 | 是 |
| `TeamVotesRevealed` | 全部个人投票 | 相同 | 是 |
| `MissionChoiceSubmitted` | 否 | 仅提交者确认自己的选择 | 是 |
| `MissionSucceeded/Failed` | 结果和失败票数量 | 相同 | 包含内部明细引用 |
| `PlayerSpoke` | 是 | 是 | 是 |
| `GameEnded` | 是 | 是 | 是 |

Event Store 可以保存内部事实，但 Projection 必须依据查看者生成不同 Payload。

## 23. 持久化模型

```sql
action_batch(
  batch_id,
  game_id,
  source_game_version,
  turn_token,
  phase,
  action_type,
  required_players_json,
  input_snapshot_ref,
  disclosure_policy,
  status,
  deadline,
  batch_version,
  created_at,
  committed_game_version
)

action_submission(
  batch_id,
  player_id,
  idempotency_key,
  controller_execution_id,
  action_json,
  submitted_at,
  submission_version
)
```

约束：

- `(batch_id, player_id)` 唯一。
- `(batch_id, idempotency_key)` 唯一。
- 一个 Game 只能有一个 `OPEN/PARTIALLY_COLLECTED/COMPLETED` Batch。

## 24. Run Budget

```java
public record RunBudget(
        int maxAutomaticTransitions,
        int maxControllerDispatches,
        Duration maxWallClockTime,
        BigDecimal maxEstimatedCost
) {}
```

`runUntilBlocked` 达到任何限制都应安全返回当前状态，不把游戏标记为失败。

## 25. 可观测性

Metrics：

```text
avalon.coordinator.advance
avalon.coordinator.auto_transitions
avalon.action_batch.open
avalon.action_batch.partial
avalon.action_batch.completed
avalon.action_batch.expired
avalon.action_batch.invalidated
avalon.action_submission.latency
avalon.action_submission.retry
avalon.game.terminal
```

Trace：

```text
game.advance
├── game.load
├── rule.next_requirement
├── action_batch.open/load
├── controller.dispatch
├── action_submission.persist
├── rule.decide_completed_batch
├── event.append
└── action_batch.commit
```

日志不得包含未公开的其他玩家 Submission。

## 26. 可选 LLM Narrator

系统可以增加 `GameNarrator` 或 `ObserverAgent`：

- 读取 Public Game View。
- 生成公开播报或赛后分析。
- 没有 GameCoordinator、RuleEngine、Repository 或 ActionCollector 写权限。

它不是 Game Manager，不能参与裁定。

## 27. 验收场景

1. 同一组队投票 Batch 中，五个 Agent 使用相同公共快照。
2. 后提交者无法读取先提交者的票。
3. 收齐前公共事件流不出现个人投票。
4. 收齐后一次性公开全部组队投票。
5. 任务结束只公开失败票数量，不公开来源。
6. 好人提交 FAIL 被规则引擎拒绝且 Batch 不提交领域。
7. 一个 LLM 超时时只重试该玩家，其他合法提交保留。
8. Human 与 LLM 混合 Batch 可以跨应用重启恢复。
9. Completed Batch 在领域提交前崩溃，恢复后只提交一次。
10. 旧 Game Version 的 Batch 被失效。
11. 重复 Submission 不重复计票。
12. 三次任务失败后不再创建新 Batch。
13. 三次任务成功后进入刺杀而不是直接结束。
14. 刺杀结果产生唯一终局事件。
15. Function Call 模式只能产生 ProposedAction，不能直接修改游戏。

## 28. 完成定义

只有同时满足以下条件，游戏编排才算完成：

- GameCoordinator 是确定性应用服务，不是 LLM Agent。
- GameRuleEngine 是状态转换和胜负的唯一裁判。
- NextRequirement 明确区分自动、单人、并行、外部等待和终局。
- 所有玩家动作通过持久化 Action Batch 收集。
- 组队投票和任务行动遵守逻辑并行。
- 批次内未公开 Submission 不进入其他 Agent 的观察上下文。
- Structured Output 或 Function Call 最终都只产生 ProposedAction。
- 批次支持幂等、超时、失败、混合 Controller 和崩溃恢复。
- 公开投票与秘密任务票采用不同 Disclosure Policy。
- 终局后所有旧 Batch、Turn Token 和 Controller Dispatch 失效。
