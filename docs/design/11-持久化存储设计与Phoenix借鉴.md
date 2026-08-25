# 11 - 持久化存储设计与 Phoenix 借鉴

## 1. 先给结论

Avalon 的第一版采用以下持久化决策：

1. **Console 和本地实验默认使用 SQLite 文件。** 不要求先安装 PostgreSQL，克隆项目后即可创建游戏。
2. **一个实验场（Arena）默认对应一个 SQLite 文件。** 同一个文件可以保存多局游戏，但每条记录都必须带 `gameId`。
3. **游戏领域数据、Agent 私有认知和 Agent 执行追踪先放在同一个物理数据库中，按逻辑数据域和 Repository 隔离。** 这样可以在一次短事务中原子提交游戏事件、动作结果和认知状态版本。
4. **PostgreSQL 是可替换的服务器后端，而不是另一套领域模型。** SQLite 和 PostgreSQL 共享同一组存储接口、事件格式和迁移版本。
5. **YAML 仍然是 Provider、Model、Agent、Harness、Skill 和 Rule Pack 的配置来源。** 数据库只保存开局时解析后的不可变配置快照，不把 API Key 或运行中的配置漂移写回数据库。
6. **Agent 没有 Phoenix 式资源权限系统。** Avalon 需要的是“玩家作用域的数据访问契约”，用于维护信息差；它不是文件写入审批、资源授权或回滚机制。

这意味着开发时可以得到类似 Phoenix 的独立 SQLite 文件体验，同时不会让数据库设计反过来破坏阿瓦隆的信息不对称。

## 2. Phoenix 当前实现提供了什么参考

在当前 Phoenix checkout 中，AI 执行存储由 `AiExecutionDatabasePathResolver` 和
`AiSqliteDatabaseManager` 管理。默认路径模式是：

```text
<user-home>/.phoenix/ai/
├── settings.sqlite
└── projects/
    └── <readable-project-slug>--<sha256-prefix>/
        ├── store-v6.sqlite
        └── blobs/
```

项目库按工作目录生成可读 slug 加哈希，避免不同项目碰撞；设置库与项目库分开。项目库启动时创建 transcript、execution、compaction、task/plan、agent、preference 和 projection 等表。

当前实现中值得 Avalon 学习的不是“表越多越好”，而是以下边界：

| Phoenix 机制 | 对 Avalon 的启发 |
| --- | --- |
| `session`、`run` 分层 | 把一局游戏、一个玩家 Agent 实例和一次决策运行区分开 |
| `tool_call` 独立表 | 工具调用拥有自己的生命周期、错误、超时和恢复状态 |
| `model_request` 与 payload 分离 | 结构化指标和大体积原始内容可以采用不同保留策略 |
| `retry_of_request_id` | 重试属于同一个 Run，但每次 Provider 请求仍有独立 ID |
| `conversation_compaction` | 摘要必须记录覆盖边界、状态和创建它的 Run |
| 外键、CHECK、唯一索引、触发器 | 将运行状态不变量尽量下沉到存储层 |
| `blobs/` 与数据库元数据分离 | 大内容不必挤在核心行中，可用 hash 和相对路径引用 |
| 数据库路径解析和 reset/close 生命周期 | 本地项目切换、备份、恢复和清理有明确边界 |

Phoenix 中的 `permission_profile`、`tool_call_document_change`、`task/plan` 等设计是 Coding Agent 的需要，不能原样引入 Avalon。

本设计的 Phoenix 源码参考点是：

- `D:\Code\GrapeCity\phoenix\phoenix-backend\phoenix-module\phoenix-ai\phoenix-ai-designer-biz\src\main\java\com\grapecity\phoenix\designer\ai\persistence\storage\AiExecutionDatabasePathResolver.java`
- `D:\Code\GrapeCity\phoenix\phoenix-backend\phoenix-module\phoenix-ai\phoenix-ai-designer-biz\src\main\java\com\grapecity\phoenix\designer\ai\persistence\storage\AiSqliteDatabaseManager.java`
- `D:\Code\GrapeCity\phoenix\phoenix-backend\phoenix-module\phoenix-ai\phoenix-ai-designer-biz\src\main\java\com\grapecity\phoenix\designer\ai\persistence\storage\AiSqliteTranscriptSchemaInitializer.java`
- `D:\Code\GrapeCity\phoenix\phoenix-backend\phoenix-module\phoenix-ai\phoenix-ai-designer-biz\src\main\java\com\grapecity\phoenix\designer\ai\persistence\storage\AiSqliteExecutionSchemaInitializer.java`
- `D:\Code\GrapeCity\phoenix\phoenix-backend\phoenix-module\phoenix-ai\phoenix-ai-designer-biz\src\main\java\com\grapecity\phoenix\designer\ai\persistence\storage\AiSqliteCompactionSchemaInitializer.java`
- `D:\Code\GrapeCity\phoenix\phoenix-backend\phoenix-module\phoenix-ai\phoenix-ai-designer-biz\src\main\java\com\grapecity\phoenix\designer\ai\execution\repository\AiExecutionStoreRepository.java`

## 3. Avalon 的物理目录布局

### 3.1 Arena 是什么

`Arena` 是一个本地实验和运行边界，可以代表一个工作目录、一个研究项目或一个部署实例。它不是玩家，也不是一局游戏。默认一个 Arena 可以包含多局游戏，便于比较不同 seed、模型和 Harness。

推荐目录：

```text
<avalon-data-directory>/
├── arenas/
│   └── <arena-slug>--<sha256-prefix>/
│       ├── avalon-v1.sqlite
│       ├── blobs/
│       └── exports/
├── cache/
└── logs/
```

默认的 `<avalon-data-directory>` 为：

```text
Windows: %USERPROFILE%/.avalon-agent/
Linux:   $HOME/.avalon-agent/
```

可通过 `avalon.persistence.data-directory` 覆盖。`arena-slug` 只用于人类识别，哈希才是稳定身份的一部分；路径中不直接暴露完整工作目录。

### 3.2 为什么不是每个 Agent 一个文件

第一版不按 Agent 拆 SQLite 文件，原因有三点：

- 提交一个合法动作时，需要同时写入领域事件、事件序号、Run 结果和认知状态版本；单库可以用一个短事务完成。
- 回放和离线评测需要把公共事件与某个 Agent 的私有执行记录关联起来。
- 多个物理文件不能自然提供跨文件原子性，容易出现“游戏动作已提交、认知状态未提交”的半完成状态。

物理上同库不等于信息上共享。Agent 只能通过带有 `AgentExecutionScope` 的 Repository 获得自己的行；模型 Tool 不允许直接获得数据库连接或 SQL。

未来如果需要把单局导出为可移动实验包，可以增加 `database-scope: GAME`，将一局复制到独立文件，但这属于导出/隔离部署模式，不改变默认领域契约。

### 3.3 与 Phoenix `settings.sqlite` 的区别

Avalon 的 Provider 和 Agent 配置由 YAML、环境变量和外部 secret 文件提供，因此第一版**不需要** Phoenix 式的 `settings.sqlite`：

- `providers.yml`、`models.yml`、`agents.yml` 是可审查、可版本控制的配置资产。
- API Key 只通过 `CredentialResolver` 解析，数据库只保留 `credentialRef`。
- 游戏开始时把解析后的非秘密配置写入 `game_configuration_snapshot`，保证配置文件后来修改不会改变历史游戏。

将来若增加 GUI 配置编辑器，可以新增配置目录或设置库，但它不能成为历史游戏配置的唯一来源，也不能覆盖已冻结的快照。

## 4. 逻辑数据域

所有数据暂时共用一个 SQLite 文件，但必须按下面的逻辑域划分。每个域有独立 Repository，禁止跨域直接拼装 SQL。

| 数据域 | 权威性 | 可被普通 Agent 读取吗 | 典型内容 |
| --- | --- | --- | --- |
| Game Domain Store | 游戏唯一真相 | 不能直接读取 | 规则状态、角色分配、事件、快照、Turn Token |
| Action Collection Store | 待裁定动作 | 只能确认自己的提交 | Action Batch、个人 Submission、披露策略和截止时间 |
| Public Projection | 可重建投影 | 只能读取当前可见部分 | 公共时间线、公开发言、任务结果、公开投票 |
| Player Cognitive Store | 玩家主观状态 | 只能读取自己的作用域 | Belief、Strategy、Memory、Cover Story、观察游标 |
| Agent Execution Store | 运行控制和追踪 | 只能读取自己的 Run；管理员通过专用查询 | Run、Tool Call、Model Request、Checkpoint、验证结果 |
| Admin Audit Store | 管理审计 | 不注册为 Agent Tool | 脱敏诊断、成本、失败原因、系统操作 |
| Blob Store | 大对象承载 | 默认不能读取 | 可选的脱敏模型 payload、导出文件、附件 |
| Configuration Source | 配置资产 | 由宿主解析，不由 Agent 查询 | YAML、环境变量、Secret Ref |

“共用一个文件”只是部署选择；逻辑上 `Game Domain Store` 和 `Player Cognitive Store` 仍然是两个不同的信任边界。

## 5. 后端抽象

领域层只依赖端口，具体 SQLite 或 PostgreSQL 实现在基础设施层：

```java
public interface GameEventStore {
    AppendResult append(GameId gameId, long expectedVersion, List<DomainEvent> events);
    List<StoredEvent> loadAfter(GameId gameId, long sequence);
}

public interface PlayerCognitionRepository {
    Optional<PlayerCognitionState> find(AgentExecutionScope scope);
    void save(AgentExecutionScope scope, PlayerCognitionState state, long expectedStateVersion);
}

public interface ActionBatchRepository {
    ActionBatch open(ActionBatch batch);
    Optional<ActionBatch> findActive(GameId gameId);
    SubmissionResult submit(ActionSubmission submission, long expectedBatchVersion);
    void markCommitted(ActionBatchId batchId, long committedGameVersion);
}

public interface AgentExecutionRepository {
    AgentRun create(AgentExecutionScope scope, AgentRun run);
    Optional<AgentRun> find(AgentExecutionScope scope, AgentRunId runId);
    void appendToolCall(AgentExecutionScope scope, PersistedToolCall toolCall);
    void complete(AgentExecutionScope scope, AgentRunCompletion completion);
}

public interface CheckpointRepository {
    Optional<PlayerCognitionCheckpoint> latest(AgentExecutionScope scope);
    void save(AgentExecutionScope scope, PlayerCognitionCheckpoint checkpoint);
}
```

实现建议使用 Spring JDBC 和显式 mapper；领域对象不使用 JPA 注解，数据库行也不向上层泄漏。SQLite/PostgreSQL 的差异由 Repository 实现和迁移脚本吸收，而不是让 Harness 判断数据库类型。

## 6. SQLite 运行要求

SQLite 适合本项目的原因是：首要场景是单机 Console、游戏写入短小、模型调用不占用事务，而且读多写少。它不是因为“游戏数据不重要”才被选择。

必须启用或实现：

- `PRAGMA journal_mode=WAL`，允许回放和查询在写入时读取已提交内容。
- `PRAGMA foreign_keys=ON`，否则外键约束默认可能不生效。
- 合理的 `busy_timeout` 和有限的数据库写重试。
- 每局或每个命令的短事务；禁止在数据库事务中等待 LLM、SSE 或 Human 输入。
- `expectedVersion`、`turnToken` 和幂等键校验必须在同一提交事务中完成。
- 数据库初始化和迁移使用版本号，禁止 `ddl-auto=update`。

SQLite 只有一个写入者。并发模型调用可以并行，但动作提交、事件追加和认知提交仍会在短写事务中排队；遇到 `SQLITE_BUSY` 时只重试存储提交，不重新调用模型，也不重复应用动作。

当部署为多实例、需要大量并发人类玩家或独立备份策略时，切换到 PostgreSQL：

```text
GameEventStore(SQLite)      -> GameEventStore(PostgreSQL)
PlayerCognitionRepository   -> PlayerCognitionRepository(PostgreSQL)
AgentExecutionRepository    -> AgentExecutionRepository(PostgreSQL)
```

接口、事件版本和作用域不变。H2 不作为长期的“SQLite 替代品”，因为两者的锁、JSON、UPSERT 和事务行为差异会掩盖真实问题。

## 7. 推荐的核心表

下面是 Avalon 第一版的最小表集。表名和字段可以在实现时调整，但信息域、作用域和生命周期不能省略。

### 7.1 游戏领域表

```sql
game(
  game_id TEXT PRIMARY KEY,
  arena_id TEXT NOT NULL,
  status TEXT NOT NULL,
  rule_pack_id TEXT NOT NULL,
  rule_pack_version TEXT NOT NULL,
  setup_id TEXT NOT NULL,
  setup_version TEXT NOT NULL,
  seed TEXT NOT NULL,
  aggregate_version INTEGER NOT NULL,
  next_event_sequence INTEGER NOT NULL,
  configuration_snapshot_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
)

game_player(
  game_id TEXT NOT NULL,
  player_id TEXT NOT NULL,
  seat_no INTEGER NOT NULL,
  controller_type TEXT NOT NULL,
  display_name TEXT NOT NULL,
  agent_instance_id TEXT,
  PRIMARY KEY(game_id, player_id),
  UNIQUE(game_id, seat_no)
)

game_role_assignment(
  game_id TEXT NOT NULL,
  player_id TEXT NOT NULL,
  role_id TEXT NOT NULL,
  visibility TEXT NOT NULL DEFAULT 'ENGINE_ONLY',
  PRIMARY KEY(game_id, player_id),
  FOREIGN KEY(game_id, player_id) REFERENCES game_player(game_id, player_id)
)

game_event(
  game_id TEXT NOT NULL,
  sequence_no INTEGER NOT NULL,
  aggregate_version INTEGER NOT NULL,
  event_type TEXT NOT NULL,
  event_version INTEGER NOT NULL,
  visibility TEXT NOT NULL,
  actor_player_id TEXT,
  audience_player_id TEXT,
  payload_json TEXT NOT NULL,
  occurred_at TEXT NOT NULL,
  correlation_id TEXT,
  causation_id TEXT,
  PRIMARY KEY(game_id, sequence_no)
)

game_snapshot(
  game_id TEXT PRIMARY KEY,
  aggregate_version INTEGER NOT NULL,
  snapshot_version INTEGER NOT NULL,
  state_json TEXT NOT NULL,
  created_at TEXT NOT NULL
)

action_batch(
  batch_id TEXT PRIMARY KEY,
  game_id TEXT NOT NULL,
  source_game_version INTEGER NOT NULL,
  turn_token TEXT NOT NULL,
  phase TEXT NOT NULL,
  action_type TEXT NOT NULL,
  required_players_json TEXT NOT NULL,
  input_snapshot_ref TEXT NOT NULL,
  disclosure_policy TEXT NOT NULL,
  status TEXT NOT NULL,
  deadline TEXT,
  batch_version INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  committed_game_version INTEGER,
  UNIQUE(game_id, turn_token)
)

action_submission(
  batch_id TEXT NOT NULL,
  player_id TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  controller_execution_id TEXT,
  action_json TEXT NOT NULL,
  submitted_at TEXT NOT NULL,
  submission_version INTEGER NOT NULL,
  PRIMARY KEY(batch_id, player_id),
  UNIQUE(batch_id, idempotency_key),
  FOREIGN KEY(batch_id) REFERENCES action_batch(batch_id)
)

pending_player_action(
  batch_id TEXT NOT NULL,
  player_id TEXT NOT NULL,
  allowed_action_schema_json TEXT NOT NULL,
  status TEXT NOT NULL,
  expires_at TEXT,
  created_at TEXT NOT NULL,
  PRIMARY KEY(batch_id, player_id),
  FOREIGN KEY(batch_id) REFERENCES action_batch(batch_id)
)
```

`game_role_assignment` 可以被领域服务和管理员读取，但绝不能直接成为 Agent Tool 的数据源。公共回放只能消费 `Public Projection`，不能把 `game_event.payload_json` 原样返回。

所有单人和多人动作都通过 `action_batch` 收集。`pending_player_action` 只表示 Human/Remote 的外部等待入口，不再独立拥有游戏推进语义。组队投票在 Batch 完成前不公开任何 `action_submission`；任务行动永远只公开聚合结果。完整契约见 [GameCoordinator 与动作收集规范](12-GameCoordinator与动作收集.md)。

### 7.2 玩家认知表

```sql
player_agent_instance(
  game_id TEXT NOT NULL,
  owner_player_id TEXT NOT NULL,
  agent_instance_id TEXT NOT NULL,
  agent_profile_id TEXT NOT NULL,
  agent_profile_version TEXT NOT NULL,
  model_profile_id TEXT NOT NULL,
  harness_profile_id TEXT NOT NULL,
  resolved_configuration_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  PRIMARY KEY(game_id, owner_player_id, agent_instance_id),
  FOREIGN KEY(game_id, owner_player_id) REFERENCES game_player(game_id, player_id)
)

player_cognition_state(
  game_id TEXT NOT NULL,
  owner_player_id TEXT NOT NULL,
  agent_instance_id TEXT NOT NULL,
  state_version INTEGER NOT NULL,
  last_observed_sequence INTEGER NOT NULL,
  belief_state_json TEXT NOT NULL,
  strategy_state_json TEXT NOT NULL,
  public_commitments_json TEXT NOT NULL,
  cover_story_json TEXT,
  updated_at TEXT NOT NULL,
  PRIMARY KEY(game_id, owner_player_id, agent_instance_id),
  FOREIGN KEY(game_id, owner_player_id, agent_instance_id)
    REFERENCES player_agent_instance(game_id, owner_player_id, agent_instance_id)
)

player_memory(
  memory_id TEXT PRIMARY KEY,
  game_id TEXT NOT NULL,
  owner_player_id TEXT NOT NULL,
  agent_instance_id TEXT NOT NULL,
  memory_kind TEXT NOT NULL,
  subject_player_id TEXT,
  summary TEXT NOT NULL,
  evidence_refs_json TEXT NOT NULL,
  confidence REAL,
  created_at_sequence INTEGER NOT NULL,
  expires_after_sequence INTEGER,
  memory_version INTEGER NOT NULL,
  FOREIGN KEY(game_id, owner_player_id, agent_instance_id)
    REFERENCES player_agent_instance(game_id, owner_player_id, agent_instance_id)
)

player_cognition_checkpoint(
  checkpoint_id TEXT PRIMARY KEY,
  game_id TEXT NOT NULL,
  owner_player_id TEXT NOT NULL,
  agent_instance_id TEXT NOT NULL,
  observed_through_sequence INTEGER NOT NULL,
  cognition_state_version INTEGER NOT NULL,
  private_view_fingerprint TEXT NOT NULL,
  checkpoint_schema_version INTEGER NOT NULL,
  checkpoint_json TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('ACTIVE', 'SUPERSEDED')),
  created_by_run_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY(game_id, owner_player_id, agent_instance_id)
    REFERENCES player_agent_instance(game_id, owner_player_id, agent_instance_id)
)
```

所有主键、唯一索引和查询索引都应以 `game_id + owner_player_id + agent_instance_id` 为作用域起点。`player_cognition_checkpoint` 不是游戏快照，也不是公共 Memory；它只回答“这个座位的下一次决策如何恢复”。

### 7.3 Agent Run、Tool Call 和 Model Request

```sql
agent_run(
  run_id TEXT PRIMARY KEY,
  game_id TEXT NOT NULL,
  owner_player_id TEXT NOT NULL,
  agent_instance_id TEXT NOT NULL,
  action_batch_id TEXT NOT NULL,
  turn_token TEXT NOT NULL,
  base_game_version INTEGER NOT NULL,
  observed_from_sequence INTEGER NOT NULL,
  observed_through_sequence INTEGER NOT NULL,
  private_view_fingerprint TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN (
    'CREATED', 'RUNNING', 'WAITING_TOOL', 'WAITING_MODEL',
    'COMPLETED', 'FAILED', 'INTERRUPTED', 'SUPERSEDED'
  )),
  attempt_no INTEGER NOT NULL,
  proposed_action_json TEXT,
  failure_code TEXT,
  failure_message TEXT,
  created_at TEXT NOT NULL,
  started_at TEXT,
  completed_at TEXT,
  UNIQUE(run_id, game_id, owner_player_id, agent_instance_id),
  UNIQUE(action_batch_id, owner_player_id, attempt_no),
  FOREIGN KEY(action_batch_id) REFERENCES action_batch(batch_id),
  FOREIGN KEY(game_id, owner_player_id, agent_instance_id)
    REFERENCES player_agent_instance(game_id, owner_player_id, agent_instance_id)
)

agent_run_artifact(
  artifact_id TEXT PRIMARY KEY,
  run_id TEXT NOT NULL,
  game_id TEXT NOT NULL,
  owner_player_id TEXT NOT NULL,
  agent_instance_id TEXT NOT NULL,
  artifact_kind TEXT NOT NULL CHECK(artifact_kind IN (
    'OBSERVATION_MANIFEST', 'EVIDENCE_SET', 'BELIEF_DRAFT',
    'STRATEGY_DRAFT', 'COMMUNICATION_PLAN', 'VALIDATION_RESULT',
    'PROPOSED_ACTION', 'CONTEXT_MANIFEST'
  )),
  schema_version INTEGER NOT NULL,
  payload_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY(run_id, game_id, owner_player_id, agent_instance_id)
    REFERENCES agent_run(run_id, game_id, owner_player_id, agent_instance_id)
)

agent_tool_call(
  tool_call_id TEXT PRIMARY KEY,
  run_id TEXT NOT NULL,
  game_id TEXT NOT NULL,
  owner_player_id TEXT NOT NULL,
  agent_instance_id TEXT NOT NULL,
  ordinal INTEGER NOT NULL,
  tool_name TEXT NOT NULL,
  arguments_json TEXT NOT NULL,
  result_json TEXT,
  player_view_version INTEGER NOT NULL,
  status TEXT NOT NULL CHECK(status IN (
    'CREATED', 'RUNNING', 'COMPLETED', 'FAILED', 'TIMEOUT', 'CANCELLED'
  )),
  error_code TEXT,
  created_at TEXT NOT NULL,
  started_at TEXT,
  completed_at TEXT,
  UNIQUE(run_id, ordinal),
  FOREIGN KEY(run_id, game_id, owner_player_id, agent_instance_id)
    REFERENCES agent_run(run_id, game_id, owner_player_id, agent_instance_id)
)

agent_model_request(
  model_request_id TEXT PRIMARY KEY,
  run_id TEXT NOT NULL,
  game_id TEXT NOT NULL,
  owner_player_id TEXT NOT NULL,
  agent_instance_id TEXT NOT NULL,
  retry_of_request_id TEXT,
  provider_id TEXT NOT NULL,
  model TEXT NOT NULL,
  protocol TEXT NOT NULL,
  request_kind TEXT NOT NULL CHECK(request_kind IN ('DECISION', 'COMPACTION', 'REPAIR')),
  provider_response_id TEXT,
  continuation_key_hash TEXT,
  status TEXT NOT NULL CHECK(status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'INTERRUPTED')),
  prompt_tokens INTEGER,
  completion_tokens INTEGER,
  total_tokens INTEGER,
  usage_estimated INTEGER NOT NULL DEFAULT 0,
  error_code TEXT,
  error_message TEXT,
  started_at TEXT NOT NULL,
  completed_at TEXT,
  FOREIGN KEY(run_id, game_id, owner_player_id, agent_instance_id)
    REFERENCES agent_run(run_id, game_id, owner_player_id, agent_instance_id),
  FOREIGN KEY(retry_of_request_id) REFERENCES agent_model_request(model_request_id)
)

agent_model_payload(
  model_request_id TEXT PRIMARY KEY,
  request_json TEXT,
  response_json TEXT,
  content_policy TEXT NOT NULL CHECK(content_policy IN ('NONE', 'REDACTED', 'RAW_LOCAL_ONLY')),
  payload_hash TEXT,
  blob_path TEXT,
  created_at TEXT NOT NULL,
  FOREIGN KEY(model_request_id) REFERENCES agent_model_request(model_request_id)
)
```

`agent_tool_call` 即使返回的是公共证据，默认仍属于 `PLAYER_PRIVATE`。某个玩家查询了哪一项证据本身可能泄露其策略；公共回放只展示规则事件，不展示查询轨迹。

子表重复保存 Run 的三元作用域不是冗余失误：它使 `agent_run_artifact`、`agent_tool_call` 和 `agent_model_request` 可以用复合外键证明自己确实属于同一个 Run、同一个座位和同一个 Agent 实例。Repository 仍必须在查询条件中携带该作用域；外键只能防止不一致写入，不能替代读取时的隔离。

### 7.4 管理审计表

```sql
admin_audit_event(
  audit_id TEXT PRIMARY KEY,
  game_id TEXT,
  actor_type TEXT NOT NULL,
  actor_id TEXT,
  event_type TEXT NOT NULL,
  redacted_payload_json TEXT NOT NULL,
  correlation_id TEXT,
  occurred_at TEXT NOT NULL
)
```

`admin_audit_event` 不注册为任何 Agent Tool。管理员查看某个 Agent 的 Run 时，必须经过 Admin Query Service；不能把管理员数据库连接注入 Harness。

## 8. 作用域契约：防止 SQLite 里的信息串线

SQLite 没有天然的行级安全策略，因此 Avalon 必须把作用域做成代码和 SQL 的硬契约，而不是依赖开发者记忆：

```java
public record AgentExecutionScope(
        String gameId,
        String ownerPlayerId,
        String agentInstanceId
) {}
```

所有私有 Repository 的查询都接收 `AgentExecutionScope`：

```java
Optional<AgentRun> find(AgentExecutionScope scope, String runId);
List<PersistedToolCall> listToolCalls(AgentExecutionScope scope, String runId);
Optional<PlayerCognitionCheckpoint> latest(AgentExecutionScope scope);
```

禁止提供以下形式的公共方法：

```java
// Forbidden: gameId alone is not an agent visibility boundary.
List<AgentRun> findByGameId(String gameId);
List<PlayerMemory> findAllMemories(String gameId);
```

SQL 也必须显式绑定作用域：

```sql
SELECT *
FROM agent_tool_call
WHERE game_id = :gameId
  AND owner_player_id = :ownerPlayerId
  AND agent_instance_id = :agentInstanceId
  AND run_id = :runId
ORDER BY ordinal;
```

另外还需要以下不变量：

- 每个座位拥有独立的 `agent_instance_id`、Runtime、Memory Namespace 和 Provider continuation namespace。
- `previous_response_id` 或其他 continuation 标识只能在同一 `gameId + ownerPlayerId + agentInstanceId` 下复用。
- `private_view_fingerprint` 由宿主根据实际投影内容计算，用于发现恢复时视图来源发生变化。
- `Evidence Ref` 必须经过 `PlayerVisibilityService` 校验，不能只相信数据库里保存的字符串。
- 游戏结束揭示角色不等于揭示其他 Agent 的 Belief、Strategy、Memory 或原始模型响应。

这套设计是信息隔离契约，不是 Phoenix 式资源权限系统：Agent 仍然不能访问文件、数据库或其他外部资源，也没有审批和回滚动作。

## 9. 与 Phoenix 表结构的逐项取舍

| Phoenix 表/概念 | Avalon 处理 | 原因 |
| --- | --- | --- |
| `session` | 改为 `game` 与 `player_agent_instance` | Avalon 的第一身份是游戏和座位，不是聊天会话 |
| `run` | 保留为 `agent_run` | 支持恢复、重试、超时、旧 Run 作废和成本统计 |
| `run_context` | 保留为 `agent_run_artifact`/`context_manifest` | 记录本次实际使用的私有视图、Skill 和工具版本 |
| `message`、`message_content` | 不作为默认核心表；公开发言写入 `game_event`，内部阶段写入 Artifact | 避免把每次模型消息误当成游戏事实或共享聊天记录 |
| `tool_call` | 保留为 `agent_tool_call` | 工具生命周期和结果必须可恢复、可审计 |
| `model_request` | 保留为 `agent_model_request` | 区分重试、Provider 响应 ID、token 使用量和失败 |
| `model_request_payload` | 可选 `agent_model_payload` | 原始 Prompt/Response 默认不保存，学习实验可显式开启脱敏或本地原始模式 |
| `conversation_compaction` | 改为每个座位的 `player_cognition_checkpoint` | Checkpoint 是私有认知恢复材料，不是共享对话摘要 |
| `agent_instance` | 保留概念，绑定到 `player_agent_instance` | 同 Profile 的两个座位也必须是两个独立实例 |
| `agent_task`、`task_list`、`plan` | V1 不引入 | Avalon 没有 Coding Agent 的文件任务分解和子任务协作需求 |
| `tool_call_document_change`、`run_document_change` | 删除 | 游戏 Tool 默认无外部副作用，不操作文档或资源 |
| `permission_profile`、`permission_rule` | 删除 Agent 资源权限层 | 与当前游戏边界无关；只保留玩家视图和工具白名单校验 |
| `provider`、`provider_model`、`settings` | 由 YAML/Secret Resolver 管理，游戏只存冻结快照 | 保持配置可审查、可版本化，避免数据库成为第二套配置系统 |
| `blob` | 保留为可选 Blob Store 元数据 | 大型脱敏 payload 和导出文件可脱离核心表保存 |

Phoenix 的大量表是因为它同时承载聊天 transcript、Coding Agent、前端工具桥、权限审批、计划执行和子 Agent。Avalon 的表数量应围绕“游戏事实、玩家认知、决策运行”设计，不能用数量衡量工程成熟度。

## 10. 与战略认知流程的配合

第 10 篇规范中的战略流程必须使用“草稿”和“正式状态”两阶段提交：

```text
ObservationBatch
-> Evidence Extraction
-> Belief Update Draft
-> Strategy / Communication Draft
-> persist private Run Artifacts
-> ProposedAction
-> GameRuleEngine validates and accepts/rejects
-> if accepted: persist formal Strategic State
-> create Player Cognition Checkpoint
```

具体规则：

1. 观察游标和公开事实可以由宿主从已可见事件确定性推进，并写入该玩家的 Run 或认知状态。
2. Belief、Strategy、Cover Story 在动作提交前只属于当前 Run 的私有 Draft；不能因为模型返回了 JSON 就直接覆盖正式状态。
3. 动作因旧 `turnToken`、`baseGameVersion` 或规则校验失败时，Run 标记为 `SUPERSEDED` 或 `FAILED`，其中的 Draft 不能覆盖最新正式认知。
4. 只有规则引擎接受动作后，才以新的 `state_version` 提交正式认知状态，并生成带 `observedThroughSequence` 和 `privateViewFingerprint` 的 Checkpoint。
5. Checkpoint 恢复时只装载当前座位自己的公开事件增量、角色允许的私有知识和自己的认知状态。

这样，持久化增加的是可恢复性，不是一个让所有 Agent 共享上下文的全局 Memory。

## 11. 一次 Agent 回合的数据库事务

模型调用永远在事务外执行：

```text
Transaction A: open Action Batch
  load current game version
  create Turn Token
  persist frozen input snapshot reference and disclosure policy
  create pending Human/Remote rows when required
  commit

Outside transaction: execute required Controllers
  build each PlayerPrivateView from the same public snapshot
  persist Run and Context Manifest for each Agent
  call tools and provider without exposing other pending submissions
  produce ProposedAction independently

Transaction B: persist one Submission
  reload Action Batch and verify batch version + Turn Token
  verify player membership and idempotency key
  persist ProposedAction
  update batch status
  commit

Transaction C: commit completed batch
  reload game and verify source game version + Turn Token
  ask GameRuleEngine to validate CompletedActionBatch
  append accepted/rejected domain events
  if accepted, save formal cognition state and checkpoint
  close related Runs with final status
  mark Action Batch COMMITTED
  commit
```

如果进程在 Provider 成功后崩溃，重启时根据 `action_batch.status`、`agent_run.status`、`turn_token` 和 `idempotency_key` 判断是重交已有 ProposedAction、只调度缺失玩家、提交 Completed Batch，还是标记旧 Run 中断；绝不能仅凭“数据库里有一段 assistant 文本”重复调用模型或重复计票。

## 12. Phoenix 模式中应该直接学习的 Schema 习惯

- 状态字段使用有限枚举和数据库 `CHECK`，不要让任意字符串进入恢复状态机。
- 时间字段至少包含 `created_at`、`updated_at`，长调用再增加 `started_at`、`completed_at`。
- 重试使用新记录和显式 `retry_of_*` 外键，不覆盖原请求。
- 大对象与运行索引分离，核心查询不要依赖完整 Prompt/Response。
- 结构化 JSON 必须带 `schema_version`，未来通过 upcaster 读取旧版本。
- 关键关联使用外键和唯一索引，例如 `(game_id, sequence_no)`、`(run_id, ordinal)`。
- 为恢复路径建立专门的状态，而不是把所有异常都写成 `FAILED`。
- 用数据库元数据记录 schema version、应用版本和迁移时间，支持复制和诊断。
- Repository 方法表达业务作用域，避免 Controller 或 Tool 直接拼 SQL。

## 13. 不应照搬的 Schema 习惯

- 不要为了“完整 Agent 平台”创建文件、文档、审批、权限、回滚和资源变更表。
- 不要创建一个跨所有玩家的 `conversation_memory` 或 `agent_context` 表。
- 不要把所有 Model Request 的原始输入输出默认写入公共审计表。
- 不要用 `game_id` 单列查询 Belief、Strategy、Tool Call 或 Checkpoint。
- 不要把 Provider 的 `previous_response_id` 当作游戏恢复的唯一依据。
- 不要把 `Public Claim` 直接写入 `game_event` 的“事实字段”；只有规则引擎确认的事实才是领域事件。

## 14. 配置示例

### 14.1 本地 Console 默认

```yaml
avalon:
  persistence:
    backend: sqlite
    data-directory: ${user.home}/.avalon-agent
    arena-key: ${user.dir}
    sqlite:
      file-name: avalon-v1.sqlite
      journal-mode: WAL
      foreign-keys: true
      busy-timeout: 5s
    snapshot-every-events: 25
    snapshot-after-round: true
  audit:
    store-raw-provider-content: false
```

### 14.2 服务器部署

```yaml
avalon:
  persistence:
    backend: postgresql

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/avalon
    username: avalon
    password: ${AVALON_DB_PASSWORD}
```

两种模式都必须保留相同的 `gameId`、`sequence`、`ownerPlayerId`、`agentInstanceId` 和事件版本语义。

## 15. 数据保留和导出

默认保留策略：

| 数据 | 默认策略 |
| --- | --- |
| 领域事件、角色分配和配置快照 | 长期保留，用于回放和争议诊断 |
| 游戏快照 | 保留最新及少量历史版本，可由事件重建 |
| Agent Run、Tool Call、结构化 Artifact | 长期或评测周期保留，默认按玩家作用域导出 |
| Belief、Strategy、Checkpoint | 保留每个座位的版本链，不能合并成全局摘要 |
| 原始 Provider Prompt/Response/Reasoning | 默认关闭；SSE reasoning 默认只实时展示和保留截断预览，开启全文保留时只存本地、按玩家作用域隔离并标记 retention policy |
| Token、延迟、错误和成本 | 可长期保留的结构化指标 |

JSONL 导出必须明确视图：

```text
game-replay-public.jsonl       # 仅公共投影
game-player-P1-private.jsonl   # P1 自己的认知和执行记录
game-admin-audit.jsonl         # 管理员审计，脱敏
```

普通 Agent 不得调用导出功能，也不能读取管理员导出的文件。

## 16. 验收不变量

实现持久化后，至少要能证明以下场景成立：

1. 应用在等待 LLM 时重启，P1 只恢复自己截至 `lastObservedSequence` 的视图。
2. P1 和 P2 使用相同 Agent Profile 时，两个 `agent_instance_id` 的 Run、Tool Call、Memory 和 continuation 完全分离。
3. P1 的 Tool 查询记录不会出现在 P2 的上下文、公共回放或普通 API 响应中。
4. 旧 `turnToken` 的 Run 即使拥有完整 ProposedAction，也不能覆盖正式 Strategic State。
5. Checkpoint 被单独删除后，可以从该座位可见的公开事件和剩余认知记录恢复或安全暂停，不能从其他 Agent 的记录补全。
6. 关闭原始 Provider 内容后，数据库和 Blob Store 中没有可还原的完整私有 Prompt/Response，但结构化 Run 状态和回放仍然可用。
7. SQLite 切换到 PostgreSQL 后，公共回放、动作幂等性和信息隔离行为不变。
8. Parallel Batch 部分收集后重启，只重新调度缺失玩家，已有合法 Submission 不丢失也不泄露。
9. Completed Batch 在领域提交前重启，恢复后只产生一次领域裁定和事件序列。
10. 组队投票收齐前 Public Projection 不包含任何个人选择，收齐后一次性公开全部投票。
11. 任务行动无论运行中还是导出后，都不向普通玩家暴露任务票来源。

这些是不变量和验收场景，不要求在设计阶段复制 Phoenix 的全部自动化测试；实现阶段应针对存储适配器和信息隔离分别建立测试。

## 17. 最终判断

Phoenix 的独立 SQLite 机制非常适合成为 Avalon 本地开发的起点，尤其适合学习：

```text
路径解析
-> 数据库生命周期
-> 显式 schema 初始化/迁移
-> Repository 分层
-> Run/Tool/Model/Compaction 的可恢复状态
-> 大对象与核心记录分离
```

但 Avalon 必须重新定义“什么是一个 Session、什么是一个 Message、谁可以读取一行数据”。最终设计不是 Phoenix 表结构的缩小版，而是：

```text
SQLite/PostgreSQL
        |
        +-- Game Domain Truth
        +-- Action Collection State
        +-- Public Projection
        +-- Player-scoped Cognition
        +-- Player-scoped Agent Execution
        +-- Admin Audit
```

数据库负责可靠保存和恢复；规则引擎负责游戏真相；Visibility Projector 负责信息边界；Harness 负责把一个玩家的私有视图变成可验证的 ProposedAction。只要这四个职责不混淆，持久化就会增强 Avalon 的可研究性，而不会消除游戏的核心信息差。
