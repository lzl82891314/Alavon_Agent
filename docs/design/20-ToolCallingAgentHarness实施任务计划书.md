# 20 - Tool Calling Agent Harness 实施任务计划书

## 1. 执行原则

- 保留 `DefaultAgentHarness`，新增实现通过同一 `AgentHarness` 接口共存。
- 先完成公共契约，再并行实现工具、协议适配、配置接入和审计。
- 不修改游戏规则和秘密信息边界。
- 不新增测试代码或运行自动化测试；仅执行编译、Checkstyle（若根 POM 配置）和 diff 检查。

## 2. 工作包

### WP0：公共契约与 Harness 选择（主 Agent 监工）

新增 Harness 类型、`PlayerAgentConfig` 字段、座位配置序列化和解析；新增 `ToolCallingAgentHarness` 接入点；默认值为 TOOL_CALLING，DEFAULT 作为显式兼容模式保留。

### WP1：Tool Registry 与权限执行器

实现 Tool Descriptor、Tool Call、Tool Result、Tool Execution Context、Tool Policy、Registry 和 Executor。提供六个只读战略工具，所有结果带可见 Sequence 来源。

### WP2：Agent Loop

实现协议无关的有限循环，处理工具调用、结果回传、最大次数、超时、Deadline、错误分类和最终动作复用校验链。保证认知失败不阻塞合法 action。

### WP3：Provider Tool Calling Adapter

扩展 OpenAI Responses、OpenAI Compatible Chat、Anthropic Messages 的请求和 SSE 解析，映射统一 Tool Call 事件。保持 Default 模式原路径不变；不支持 Tool Calling 时返回明确能力错误。

### WP4：Strategic Context Integration

把 10/15/17 中的证据、可能世界、预测反馈、角色策略、受众计划和候选行动接入 Tool 描述与 Tool Result；模型通过工具主动获取，宿主不泄露越权事实。

### WP5：审计、Console 与恢复边界

在进程内记录 Tool Call 生命周期和结果摘要，并让已完成 Agent Run 的摘要进入现有
Runtime 审计模型；扩展 INFO/DEBUG/TRACE 实时显示。核实现有恢复原语并明确第一期不支持
Tool Loop 中间态断点续跑和跨进程幂等复用。

### WP7：Tool Call 持久化恢复（后续工作）

新增 Agent Run/Tool Call 独立持久化状态、幂等键、私有结果载荷策略和恢复编排；在
`RecoveryService` 中将未完成调用标记为 `INTERRUPTED`，并仅按明确策略复用已完成的只读
结果。该工作包不属于本期实现。

### WP6：集成审查与静态验收

检查完整生产调用链、配置兼容性、权限隔离、Default 回归边界、Java 风格和 Maven 编译。确认没有只创建未消费的 DTO 或死链路。

## 3. 依赖关系

```text
WP0 -> WP1 -> WP2 -> WP3
  \-> WP4 ----^   \-> WP5
                 -> WP6
```

WP1、WP4、WP5 可在 WP0 契约稳定后并行；WP2 依赖 WP1；WP3 依赖 WP2 的统一模型结果契约；WP6 最后执行。WP7 依赖独立持久化模型设计，后续单独实施。

## 4. 每项完成条件

- WP0：配置可选择两种 Harness，缺省走 Tool Calling，显式配置可走 Default。
- WP1：六个工具可按权限执行，未知工具和越权参数拒绝。
- WP2：工具调用后能继续模型请求，最终 action 只提交一次，循环有界。
- WP3：三类 Provider 的 Tool Calling 能力显式声明，SSE 可解析工具事件。
- WP4：工具结果可驱动 10/15/17 的战略闭环，事实和模型推理边界清楚。
- WP5：已完成 Agent Run 的 Tool Call 摘要可随现有审计落库，Console 分级输出正确，
  中间 Loop 不具备跨进程恢复能力且文档边界明确。
- WP7：未完成 Tool Call 可标记中断、成功只读结果可按幂等键复用，并有私有数据保留策略。
- WP6：生产编译、静态检查、diff 审查通过，并记录未运行自动化测试。

## 5. 监工检查清单

- [x] 每个 sub agent 的实现都进入真实 `LlmPlayerController` 调用链。
- [x] Default Harness 的请求、解析、重试和动作提交语义未被破坏。
- [x] Tool 永远不能直接调用 GameRepository、ActionCollector 或 GameRuleEngine。
- [x] 私有 Tool 的身份来自宿主上下文，不来自模型参数。
- [x] Tool Result 不进入公开事件流，不覆盖正式认知状态。
- [x] 最终动作必须经过原有规则引擎。
- [x] Tool Loop 超限、Provider 能力缺失和协议错误均可分类暂停。
- [x] 真实 Provider 行为未在静态编译基础上被过度宣称。

## 6. 实施结果

WP0-WP5 已进入生产调用链：两种 Harness 可按座位共存，六个只读工具通过
`ToolPolicy` 暴露，三类 Provider 使用原生 Tool Calling 协议，有限 Agent Loop
最终复用既有动作解析、验证和提交链。Tool Call 审计摘要保存在模型元数据中，
Console INFO 显示工具语义事件，DEBUG/TRACE 显示参数增量。

`ToolCallingAgentHarness` 使用独立的初始 Prompt 模式：只内联必要身份、规则、公共快照、
当前指令和输出契约；记忆、公开历史及 10/15/17 的战略分析继续由宿主持有，并只通过
白名单 Tool 按需返回。Default Prompt 和单轮调用路径保持不变。

本轮完成的是生产代码和协议适配的静态实现；真实 Provider 的多轮 Tool Calling
兼容性与 10/15/17 战略行为收益仍需实际对局验证。

现有持久化链只在动作成功或 LLM 失败暂停后写入 Runtime 审计，不保存 Tool Loop 的每步
上下文。已完成 Run 的 `agentToolAudit` 可进入最终审计，但进程中途退出时无法恢复该次
Run，也不能复用之前完成的 Tool Result。WP7 保留为后续工作，不能以本期最终模型元数据
代替 Tool Call 级持久化与恢复。
