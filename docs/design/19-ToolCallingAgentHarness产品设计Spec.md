# 19 - Tool Calling Agent Harness 产品设计 Spec

## 1. 目标

新增 `ToolCallingAgentHarness`，让模型通过受控 Tool Call 自主完成战略认知与社会博弈闭环，同时保留现有 `DefaultAgentHarness` 单轮结构化输出模式。两种 Harness 可在同一局、不同座位分别配置。

本功能必须承载并落实以下既有设计：

- [10 - 战略认知与社会博弈](10-战略认知与社会博弈.md)
- [15 - 战略智能演进与高级社会博弈](15-战略智能演进与高级社会博弈.md)
- [17 - 多世界假设与战略决策闭环实现报告](17-多世界假设与战略决策闭环实现报告.md)

核心原则仍为：模型提出认知和动作，宿主提供权限边界、执行工具、事实校验和最终提交。

## 2. 非目标

- 不改变 Avalon 规则、胜负、角色可见性和秘密 Batch 隔离。
- 不允许 Tool 直接修改游戏状态、数据库或正式记忆。
- 不删除或改变 Default Harness 的默认行为。
- 不把模型的 Tool Result 或 `privateThought` 当作权威事实。
- 不新增外部依赖；优先复用现有 SSE、解析、校验和持久化设施。
- 第一期不提供 Tool Loop 中间检查点、进程重启后从中间 Tool Call 续跑，或已完成
  Tool Result 的跨进程幂等复用；这些能力需要独立的 Agent Run/Tool Call 持久化模型。

## 3. 用户体验

创建游戏时，每个 LLM 座位可选择：

```text
TOOL_CALLING     Tool Calling + Agent Loop（默认）
DEFAULT          单轮结构化输出（显式兼容模式）
```

未指定时使用 `TOOL_CALLING`。座位配置冻结在游戏快照中，游戏开始后配置文件变化不影响本局。

模型池使用 `SEAT_BINDING` 时，Harness 可通过 `seatHarnessBindings` 与每个座位的 modelId
一起配置；使用 `ROLE_BINDING` 时，通过 `roleHarnessBindings` 与每个角色的 modelId 一起
配置。Console 在输入每项 modelId 后立即询问对应 Harness。缺少 Harness 绑定时使用
`TOOL_CALLING`；需要原有单轮行为时必须显式绑定 `DEFAULT`。

## 4. 工具白名单

第一期只读工具：

| 工具 | 作用 | 输入 | 输出 |
|---|---|---|---|
| `get_public_timeline` | 查询玩家可见公开事件 | 可选轮次、玩家、事件类型 | 事件和 Sequence 来源 |
| `get_vote_history` | 查询公开投票与队伍历史 | 可选轮次、玩家 | 结构化投票证据 |
| `compare_player_consistency` | 比较公开发言、承诺和投票 | 玩家 ID | 证据、矛盾和来源 |
| `list_legal_actions` | 查询当前合法动作 | 无 | 当前动作 Schema |
| `get_my_memory` | 查询当前 Agent 私有结构化记忆 | 可选主题 | 受限记忆 |
| `evaluate_team_combinations` | 分析候选队伍 | 候选队伍列表 | 风险、信息覆盖和证据 |

工具由 `ToolPolicy` 按阶段、角色和 Harness Profile 生成最小集合。工具执行上下文由宿主创建，不接受模型传入的玩家身份作为权限依据。

## 5. Agent Loop

```text
Build Request
-> Model Stream
-> Parse Tool Calls or Final Action
-> Validate Tool Name and Arguments
-> Execute Read-only Tool
-> Append Tool Result to private loop context
-> Repeat until Final Action
-> Parse and validate final action
-> Persist accepted cognition after legal action commit
```

硬限制：最大循环次数、单工具超时、总 Deadline。Token 使用量和工具调用次数只记录用于观测，不作为 Agent Loop 的终止条件。达到其他限制时分类失败并按现有 LLM 失败策略暂停，不自动修改游戏。

认知 Tool Call 可以零次或多次；最终游戏动作必须恰好一次。最终动作仍复用 `ResponseParser`、`ValidationRetryPolicy`、`GameCoordinator` 和 `GameRuleEngine`。

## 6. 协议适配

引入协议无关的模型回合结果：

```text
TOOL_CALL_DELTA / TOOL_CALL_COMPLETE
FINAL_OUTPUT_DELTA / FINAL_OUTPUT_COMPLETE
MODEL_ERROR / MODEL_COMPLETED
```

OpenAI Responses、OpenAI Compatible Chat 和 Anthropic Messages 分别把 Provider 原生 Tool Call 映射为统一事件。现有不支持 Tool Calling 的 Provider 必须明确返回能力错误；Default Harness 继续使用当前 JSON 输出路径。

Tool Result 作为下一次模型请求的受控上下文传回，不能伪造为公开游戏事件。SSE 事件只用于流式观测，不改变最终校验边界。

Tool Calling 的初始 Prompt 只包含合法私有身份知识、规则、当前公共快照、阶段、讨论
指令、合法动作、最终输出契约和工具使用策略。公开历史、私有跨回合记忆、战略证据、
可能世界、预测反馈、角色策略、候选行动和受众计划保留在宿主请求中作为 Tool 数据源，
不得直接内联到初始 Prompt。这样由模型决定何时查询和组合证据，而不是重复消费 Default
Harness 已经准备好的完整战略上下文。

## 7. 战略能力要求

Tool Calling Harness 必须让模型能够主动获取并组合：

- 公开事件、投票、队伍和任务结果证据；
- 多个可能世界及其约束；
- 行为预测及 `SUPPORTED/CONTRADICTED/INCONCLUSIVE` 反馈；
- 角色目标权重和风险模式；
- 公开承诺、指控回应和受众计划；
- 候选行动的世界区分能力、阵营价值、暴露成本和后续计划。

宿主仍负责确定性证据、权限过滤、规则合法性和正式认知提交；模型负责选择何时调用工具、如何解释证据、比较候选并提出行动。

## 8. 审计、恢复与可观测性

当前实现会在一次 Agent Run 内收集每次 Tool Call 的工具名、脱敏参数键、输入观察
范围、执行状态、耗时、结果摘要、错误类型和所属 Agent Run。模型最终返回合法动作后，
这些摘要作为模型元数据进入现有 `RuntimeAuditEntry`，再由
`RuntimePersistenceService` 写入 `AuditRecordStore`。Tool Result 正文仍只存在于玩家私有
Loop 上下文，不进入公开事件或正式记忆。

现有 `RecoveryService` 只恢复游戏快照、游戏事件和玩家记忆，没有 Agent Run、Tool Call
状态、幂等键或中间 Loop 上下文的持久化原语。因此第一期的恢复边界是：已提交的游戏
动作、正式认知和已落库的最终审计可随游戏恢复；进程在 Tool Loop 中途退出时，不恢复
或复用该次中间 Tool Call，后续只能从新的 Agent Run 重新决策。不得把最终审计摘要解释
为 Tool Call 级断点续跑。

后续若实现跨进程恢复，需要新增：

- `AgentRunStore` 与 `ToolCallStore`，记录 `STARTED/SUCCEEDED/FAILED/INTERRUPTED` 状态；
- 基于游戏、玩家、阶段、观察 Sequence、工具名和脱敏参数哈希的幂等键；
- Tool Result 私有载荷的加密/可见性、保留期和清理策略；
- 模型请求、Tool Call 状态和游戏暂停之间明确的事务顺序；
- `RecoveryService` 对未完成 Agent Run 的中断标记，以及只读成功结果的复用策略。

当前调试阶段，`ToolCallingAgentHarness` 在 INFO 记录 Agent Run、每次 Loop 迭代、模型回合
类型与 Token、Tool 名称与完整参数、执行状态、完整 Tool Result、最终输出及失败原因。
这些日志包含玩家私有认知，只能进入管理员/本地日志。当前通过专用 Logback Appender
同时写入游戏 Console 和 `application.log`。协议稳定后可将
`agent_loop_*`、`agent_tool_call` 和 `agent_tool_result` 固定日志点整体降为 DEBUG；Console
流式界面的其他 INFO 仍只显示工具语义事件和最终动作，DEBUG 显示原始 SSE 增量，TRACE
显示完整协议诊断。

## 9. 验收标准

- 同一局不同座位可分别使用 DEFAULT 或 TOOL_CALLING。
- 不指定 Harness 时旧配置和旧调用链行为不变。
- Tool Calling Agent 至少能完成一次工具调用后再提交合法动作。
- 工具越权、未知工具、非法参数、超时和循环超限均被拒绝并分类记录。
- Tool 不会直接修改游戏状态或正式记忆。
- 10/15/17 的世界假设、证据、预测、社会计划和角色策略能通过 Tool 结果进入模型上下文。
- 合法 action 可在可选认知失败时提交，局部降级规则与 Default Harness 一致。
- 游戏事件、最终动作、正式认知和已完成 Agent Run 的审计可恢复、可回放、可追踪；
  Tool Call 中间态的跨进程恢复按第 8 节列为后续能力。
- Maven 编译和静态检查通过；自动化测试遵循仓库当前约束，不由本功能新增或运行。

## 10. 风险与边界

Tool Calling 增强的是模型获取证据和形成闭环的条件，不等于证明模型已具备稳定的人类级欺骗或社会推理能力。真实效果仍需多 Provider、多 seed、座位和角色轮换评测。
