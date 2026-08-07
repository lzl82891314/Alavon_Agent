# 04 - Agent Harness 运行时

## 1. 目标

Agent Harness 把一次不确定的模型调用转换为一次受控、有限、可审计的玩家决策。

它负责：

- 确定本回合使用哪个 Agent 配置。
- 只读取当前玩家有权看到的信息。
- 选择适用 Skill、Tool 和 Memory。
- 控制上下文和输出预算。
- 调用厂商无关的模型接口。
- 处理工具调用和结构化输出。
- 校验并修复非法结果。
- 返回候选动作或明确失败。

它不负责修改游戏状态或判断胜负。

## 2. Agent Profile

```java
public record AgentProfile(
        String agentId,
        String version,
        AgentIdentity identity,
        String modelProfileId,
        String harnessProfileId,
        List<String> skillIds,
        String memoryPolicyId,
        String toolPolicyId,
        Map<String, Object> parameters
) {}
```

Agent Profile 是可版本化资产。对局开始时冻结解析后的完整配置。

## 3. Harness Profile

```java
public record HarnessProfile(
        String harnessId,
        String contextPolicyId,
        String cognitionPolicyId,
        String validationPolicyId,
        String retryPolicyId,
        String failurePolicyId,
        AgentBudget budget,
        AuditLevel auditLevel,
        ProviderStateMode providerStateMode
) {}
```

不同 Agent 可以使用完全不同的 Harness。例如：

- 简单单次结构化决策。
- 允许一次工具分析后再决策。
- 高推理预算但极短公开发言。
- 不使用 Provider 会话的完全无状态 Harness。
- 使用 `previous_response_id` 优化连续回合的 OpenAI Harness。

## 4. 运行时请求与结果

```java
public record AgentExecutionRequest(
        AgentExecutionScope scope,
        ActionBatchId actionBatchId,
        TurnToken turnToken,
        long baseGameVersion,
        long observedFromSequence,
        long observedThroughSequence,
        String privateViewFingerprint,
        ObservationBatch observations,
        BeliefState priorBeliefs,
        StrategyState priorStrategy,
        AgentProfileSnapshot agentProfile,
        List<MemoryRecord> memories,
        Instant deadline
) {}

public record AgentExecutionResult(
        ProposedAction action,
        Optional<PublicSpeech> publicSpeech,
        Optional<DecisionSummary> decisionSummary,
        Optional<BeliefUpdate> beliefUpdate,
        Optional<StrategyStateUpdate> strategyUpdate,
        PrivateCognitionDraft cognitionDraft,
        AgentExecutionTrace trace
) {}

public record PrivateCognitionDraft(
        BeliefUpdate beliefUpdate,
        StrategyStateUpdate strategyUpdate,
        List<ValidatedMemoryMutation> memoryMutations,
        List<EvidenceRef> evidenceRefs
) {}
```

`AgentExecutionScope` 固定为 `gameId + ownerPlayerId + agentInstanceId`，由宿主创建，模型不能传入或修改。`AgentExecutionResult` 中的 `cognitionDraft` 只是当前 Run 的私有中间产物，不是领域事实，也不能在动作尚未被规则引擎接受时覆盖正式认知状态。

## 5. Harness Pipeline

```text
Resolve Profile
-> Build Observation
-> Resolve Skills
-> Resolve Tools
-> Retrieve Memory
-> Allocate Context Budget
-> Build Model Request
-> Execute Model Loop
-> Parse Candidate
-> Schema Validate
-> Domain Pre-Validate
-> Repair or Fail
-> Build Private Cognition Draft
-> Produce Trace
```

每个阶段通过明确组件完成，而不是全部堆在 `LlmPlayerController`。

### 5.1 战略认知是必需阶段

上述 Pipeline 不能只完成 Context 到合法动作的转换。讨论、提案、投票和刺杀等战略回合必须在 `Resolve Tools` 之后执行：

```text
Observe Delta
-> Update Beliefs
-> Detect Contradictions
-> Select Role Strategy
-> Plan Desired Audience Belief
-> Generate Public Communication and Action
```

`ContextAssembler` 必须接收公开事件增量，而不是只有当前计分快照。Belief、Strategy、Public Commitment 和 Cover Story 必须跨回合持久化。

具体数据契约和完成标准见 [战略认知与社会博弈规范](10-战略认知与社会博弈.md)。

## 6. Context Assembler

```java
public interface ContextAssembler {
    AgentContext assemble(ContextAssemblyRequest request);
}
```

输入来源分层：

1. 稳定系统契约：Agent 的职责、安全约束和输出方式。
2. Rule Pack 摘要：只包含当前游戏需要的规则。
3. 当前阶段说明：允许动作和停止条件。
4. 玩家观察：公共状态和私有知识。
5. 结构化记忆：按策略检索的少量记录。
6. Skill 指令：当前阶段所需的行为知识。
7. 修复反馈：仅在重试时加入。

其中玩家观察必须同时包含当前快照和自上次观察游标后的可见事件，至少覆盖公开发言、提案、逐人投票和任务结果。

### 6.1 结构化优先

上下文内部使用 Java Record/JSON 数据结构，最后才由 Provider Adapter 转换为协议输入。不要过早拼成一大段字符串。

### 6.2 来源标记

每个 Context Block 带来源和可见性：

```java
public record ContextBlock(
        String blockId,
        ContextBlockType type,
        VisibilityLevel visibility,
        Object content,
        int priority,
        boolean mandatory
) {}
```

这使系统可以记录“本次模型看到了什么”，并在预算不足时确定裁剪顺序。

## 7. 上下文预算

预算不是只设置模型的最大输出 Token，还需要分配输入空间：

```text
total context budget
├── stable instructions
├── current rules and allowed actions
├── current public/private view
├── recent events
├── retrieved memories
├── skill instructions
├── tool schemas
└── reserved output and reasoning capacity
```

建议裁剪顺序：

1. 删除重复或低相关性的旧发言。
2. 用事件摘要替代历史原文。
3. 减少低权重记忆。
4. 减少非必需 Tool Schema。
5. 永不删除当前合法动作、角色目标和私有知识边界。

## 8. Skill 系统

```java
public interface AgentSkill {
    SkillDescriptor descriptor();
    SkillContribution contribute(SkillContext context);
}
```

`SkillContribution` 可以提供：

- 指令块
- 建议工具
- 输出字段要求
- 记忆查询提示
- 上下文优先级调整

### 8.1 Skill 解析

Skill 来源：

- Agent Profile 显式 Skill
- 当前阶段必需 Skill
- 当前角色追加 Skill
- Harness Profile 默认 Skill

合并优先级：

```text
安全与规则约束
> 阶段必需 Skill
> 角色 Skill
> Agent 显式 Skill
> 风格 Skill
```

冲突必须在启动时或开局时失败，不能静默选择。

### 8.2 Skill 不等于 Prompt 文件

Skill 可以包含 Markdown 指令，但还必须有机器可读描述、版本、适用范围和依赖。未来可以增加 Java 实现，但第一版不需要动态加载不可信代码。

## 9. Tool 系统

```java
public interface AgentTool {
    ToolDescriptor descriptor();
    ToolResult execute(ToolExecutionContext context, JsonNode arguments);
}
```

每次回合通过 `ToolPolicy` 生成最小工具集合：

```java
public interface ToolPolicy {
    List<AgentTool> resolve(ToolPolicyContext context);
}
```

默认工具建议：

| Tool | 作用 |
| --- | --- |
| `get_public_timeline` | 读取过滤后的公开事件 |
| `get_vote_history` | 统计公开投票历史 |
| `compare_player_consistency` | 比较公开发言和投票 |
| `list_legal_actions` | 返回当前合法动作 Schema |
| `get_my_memory` | 读取自己的结构化记忆 |
| `evaluate_team_combinations` | 对候选队伍做确定性组合计算 |

工具实现从 `AgentExecutionScope` 获取玩家身份并再次校验可见性；私有 Tool 不接受模型传入的 `playerId`，也不能信任任何跨玩家参数。

认知 Tool 默认只读，可以在一个 Model Loop 中调用多次。投票、任务选择、组队和刺杀不是普通认知 Tool；它们是当前 Requirement 要求的最终游戏动作，必须且只能转换为一个 `ProposedAction`。

即使 Provider 使用 `submit_team_vote` 等 Function Call 表达最终动作，Tool Executor 也只能构造 ProposedAction，不得调用 Game Repository、ActionCollector 或 GameRuleEngine。

## 10. Model Loop

```text
model request
-> response items
   -> final structured output: exit loop
   -> tool calls: validate and execute
      -> append tool results
      -> next model request
   -> unsupported item: fail with protocol error
```

循环有明确限制：

- 最大模型调用次数
- 最大工具调用次数
- 单工具超时
- 总回合 Deadline
- 最大累计输入和输出 Token
- 最大累计成本

任何限制触发后都返回分类失败，不允许无限自治。

## 11. 结构化动作输出

优先使用 Provider 支持的 Structured Outputs。统一逻辑 Schema 示例：

```json
{
  "publicSpeech": "我支持这支队伍，但会重点观察三号玩家。",
  "action": {
    "type": "TEAM_VOTE",
    "vote": "APPROVE"
  },
  "decisionSummary": {
    "goal": "验证当前队伍",
    "evidenceRefs": ["event:18", "event:23"],
    "confidence": 0.67
  },
  "memoryProposal": {
    "observations": [
      {
        "subject": "player-3",
        "kind": "VOTE_PATTERN",
        "summary": "连续支持包含自己的队伍"
      }
    ]
  }
}
```

`decisionSummary` 和 `memoryProposal` 可选；`action` 必须存在且与当前 Allowed Action Schema 匹配。

默认使用 Structured Output 提交最终动作。Function Call 作为 Provider 兼容模式时，不得同时要求 Structured Output 重复提交同一动作。最终动作由 GameCoordinator 放入 Action Batch；多人批次的其他 Submission 对当前 Agent 不可见。

动作收集与披露规范见 [GameCoordinator 与动作收集规范](12-GameCoordinator与动作收集.md)。

## 12. 校验链

### 12.1 Protocol Validation

- Provider 响应是否完整。
- Typed Item 是否受支持。
- Tool Call 是否存在匹配结果。
- Structured Output 是否可解码。

### 12.2 Schema Validation

- 动作类型是否允许。
- 必填字段是否存在。
- 枚举和集合是否符合 Schema。

### 12.3 Domain Pre-Validation

Harness 可以调用只读的 `ActionPrevalidator` 检查：

- 是否轮到该玩家。
- 队伍人数是否正确。
- 目标玩家是否有效。
- 好人是否提交失败任务票。

最终权威校验仍由 `GameRuleEngine` 在提交命令时执行，以防并发变化。

## 13. 修复与重试

重试按失败类型决定：

| 失败 | 默认行为 |
| --- | --- |
| HTTP 瞬时错误 | 指数退避重试，受 Deadline 限制 |
| 限流 | 读取 Retry-After，必要时切换备用模型 |
| JSON/Schema 错误 | 发送最小修复反馈，再调用一次 |
| 领域非法动作 | 提供合法动作约束和错误码，再调用一次 |
| 权限或秘密泄漏 | 不重试，立即安全失败并暂停 |
| 配置错误 | 不重试，启动或开局时失败 |
| 预算耗尽 | 不重试，执行 Failure Policy |

修复上下文只包含错误码、必要字段和当前合法约束，不回显其他玩家秘密或内部堆栈。

## 14. Failure Policy

可选策略：

- `PAUSE_GAME`：默认生产策略，等待管理员处理。
- `FALLBACK_MODEL`：切换到预先配置的同能力模型。
- `DETERMINISTIC_SAFE_ACTION`：仅适用于存在无争议合法默认动作的阶段。
- `FORFEIT_PLAYER`：规则包明确支持时才可使用。
- `FAIL_GAME`：开发模式快速失败。

禁止静默伪造模型决策。例如任务动作失败时，不能未经配置自动替模型选择成功或失败。

## 15. Memory Policy

```java
public interface MemoryPolicy {
    List<MemoryRecord> retrieve(MemoryQuery query);
    List<ValidatedMemoryMutation> validate(MemoryProposal proposal, MemoryContext context);
    Optional<MemorySummary> compact(List<MemoryRecord> records, MemoryBudget budget);
}
```

### 15.1 建议记忆结构

```java
public record MemoryRecord(
        String memoryId,
        PlayerId owner,
        MemoryKind kind,
        Optional<PlayerId> subject,
        String summary,
        List<String> evidenceRefs,
        double confidence,
        long createdAtSequence,
        Optional<Long> expiresAfterSequence
) {}
```

### 15.2 写入规则

- 只能写入自己的记忆空间。
- Evidence Ref 必须引用该玩家可见事件。
- 对同一主题的重复记录进行合并。
- 置信度变化有最大步长。
- 设置单局容量和单玩家容量。
- 模型提出的“事实”默认作为推断，不升级为游戏真相。

## 16. Provider State

Provider 状态模式：

```text
STATELESS_REPLAY
PREVIOUS_RESPONSE_ID
PROVIDER_CONVERSATION
```

默认使用 `STATELESS_REPLAY` 或由 Harness 明确重建必要上下文。即使启用 OpenAI `previous_response_id`，仍要：

- 每回合重新发送稳定 instructions。
- 保存 Provider Response ID 作为优化元数据。
- 能在 Response ID 失效后从本地状态重建请求。
- 按 `gameId + ownerPlayerId + agentInstanceId` 隔离 continuation，不能让同 Profile 的不同座位共享 Provider 会话。
- 不把 Provider 会话当作审计和恢复的唯一依据。

## 17. Agent Trace

每回合保存一个逻辑 Trace：

```text
profile resolution
context blocks and hashes
skill resolution
tool exposure
model attempts
tool calls
validation results
repair attempts
accepted action
memory mutations
usage and latency
failure classification
```

敏感内容按可见性分级保存。普通玩家不能查看管理员级 Trace。

## 18. Player Controller 实现

```java
public final class LlmPlayerController implements PlayerController {
    private final AgentHarness harness;

    @Override
    public PlayerDecision decide(PlayerTurnContext context) {
        return harness.execute(map(context)).toPlayerDecision();
    }
}
```

其他实现：

- `HumanPlayerController`：返回等待外部动作的结果。
- `ScriptedPlayerController`：确定性策略，用于演示和开发验证。
- `RemotePlayerController`：未来通过外部协议请求动作。

应用层只依赖 `PlayerController`，不为 LLM 写特殊的游戏推进分支。

## 19. Harness Profile 示例

```yaml
harnessId: evidence-driven-v1
contextPolicy: balanced-context-v1
validationPolicy: strict-action-v1
retryPolicy:
  transportAttempts: 2
  repairAttempts: 1
failurePolicy: PAUSE_GAME
providerStateMode: STATELESS_REPLAY
budget:
  deadlineSeconds: 45
  maxModelCalls: 3
  maxToolCalls: 4
  maxInputTokens: 24000
  maxOutputTokens: 1200
  maxEstimatedCostUsd: 0.20
auditLevel: STANDARD
```

## 20. 与 Coding Agent 的对应关系

| Coding Agent 概念 | 本项目对应物 |
| --- | --- |
| Repository / Workspace | 当前游戏状态与事件流 |
| AGENTS.md / Instructions | Rule Pack、Agent Profile、Skill |
| Tools | 时间线、投票统计、合法动作等受控工具 |
| Sandbox | Player Private View 和 Tool Policy |
| Plan / Execute Loop | 分析、工具调用、提交结构化动作 |
| Tests / Validation | Schema 校验和 Game Rule Engine |
| Git / Audit Trail | Domain Events 和 Agent Trace |
| Context Compaction | Memory Policy 与事件摘要 |

这个映射是本项目最重要的教学成果之一。
