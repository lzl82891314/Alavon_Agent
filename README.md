# Avalon Agent Platform

这是一个用于学习和实践 Agent Engineering、Harness Engineering、多模型编排与可扩展游戏运行时的 Spring 项目。

当前仓库已实现可运行的控制台游戏基础、规则配置、模型随机池和协议 Adapter 层。正式 Agent 主链路已经接入逐玩家观察增量、三层事实、结构化战略记忆、角色策略、多轮讨论协议、接受后提交与私有信息边界，并支持 `DEFAULT` 与 `TOOL_CALLING` 两种 Harness 按座位共存；真实模型对照评测、GUI 和更多规则包仍处于后续演进范围。设计与实施入口：

投票和任务动作采用冻结批次，角色知识按阵营/精确角色/候选歧义分别投影；Memory 送模窗口默认限制为 12,000 字符。信念变化必须提交逐玩家、与目标玩家结构相关且可见的 `beliefEvidenceReferences`。`avalon-testkit` 提供协议门槛与战略门槛两级评测；审计和玩家私有视图只通过显式管理员 capability 服务读取。真实 Provider 结果只有在启用模型档案并配置相应密钥后才会产生。

- [设计文档总览](docs/design/README.md)
- [实施路线图](docs/design/08-实施路线图与学习实验.md)
- [完整配置示例](docs/design/09-完整配置示例.md)
- [战略认知与社会博弈规范](docs/design/10-战略认知与社会博弈.md)
- [Tool Calling Agent Harness 产品设计](docs/design/19-ToolCallingAgentHarness产品设计Spec.md)
- [GameCoordinator 与动作收集规范](docs/design/12-GameCoordinator与动作收集.md)
- [持久化存储设计与 Phoenix 借鉴](docs/design/11-持久化存储设计与Phoenix借鉴.md)
- [战略 Agent 第一版代码评审](docs/reviews/2026-08-08-战略Agent第一版代码评审.md)

项目默认以经典 5 人阿瓦隆为第一个规则包，但架构支持扩展人数、角色、规则、LLM Provider、Agent Harness、真人玩家、Console、REST/SSE 和未来 GUI。

模型协议当前由 `protocol` 显式选择，而不是按厂商名称猜测：支持 `OPENAI_RESPONSES`、`OPENAI_COMPATIBLE_CHAT` 和原生 `ANTHROPIC_MESSAGES`。Claude 的默认档案使用 Anthropic Messages API；只有明确配置 `OPENAI_COMPATIBLE_CHAT` 的模型才会调用 `/chat/completions`。详见[模型协议与配置体系](docs/design/05-模型协议与配置体系.md)。

REST 人工玩家必须在创建座位时提供至少 24 字符的 `actionToken`，提交动作时通过 `X-Player-Token` 发送。服务端只在冻结的玩家配置中保存带游戏和玩家盐值的 SHA-256 摘要，不回传或记录原始令牌。

LLM 座位通过 `players[].agentConfig.harnessType` 选择 Harness：`DEFAULT` 使用原有单轮结构化输出，`TOOL_CALLING` 使用只读战略工具和有界 Agent Loop。字段缺失时默认使用 `TOOL_CALLING`；需要旧模式时必须显式选择 `DEFAULT`。Console 执行 `new` 时，`seat` 和 `role` 绑定模式会在每个 modelId 之后继续询问对应 Harness；`custom` 模式中的模型池绑定沿用相同流程。Agent Loop 和 Tool 的详细 INFO 同时输出到游戏终端与 `application.log`。
