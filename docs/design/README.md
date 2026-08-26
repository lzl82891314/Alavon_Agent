# LLM 阿瓦隆平台设计文档

## 1. 文档目的

本项目不是一个“让几个大模型互相聊天”的演示程序，而是一套用于学习和实践 Agent Engineering、Harness Engineering、确定性领域建模、多模型编排与可恢复运行时的完整工程样例。

阿瓦隆适合作为这套工程的载体，原因是：

- 游戏规则确定，但玩家决策具有高度不确定性。
- 每个玩家拥有不同的私有信息，天然要求严格的信息隔离。
- 每个阶段允许的动作不同，天然适合结构化输出和工具权限控制。
- 胜负必须由可信规则引擎裁定，不能由 LLM 自己解释。
- 同一局中可以混用不同厂商、不同模型、不同 Harness 和真人玩家。
- 对局天然可用于回放、评测、成本比较和 Agent 行为研究。

## 2. 核心架构结论

项目采用 Spring Boot 模块化单体，但 Spring 只负责装配、配置、事务、交付通道和基础设施集成。游戏领域层、规则层和 Harness 核心保持纯 Java，不依赖 Spring AI 或具体模型 SDK。

```text
Console / REST / SSE
        |
        v
Application Use Cases
        |
        +--------------------+
        |                    |
        v                    v
Game Domain            Agent Harness
        |                    |
        v                    v
Rule Packs              Model SPI
        |                    |
        +----------+---------+
                   |
                   v
          Events / Snapshots / Audit
```

系统必须遵守以下不可破坏的原则：

1. 游戏领域状态是唯一真相，Prompt 和模型会话不是数据库。
2. 模型只能提出动作，规则引擎负责校验和产生状态变化。
3. 玩家私有视图由服务端计算，模型不得自行推断其可见范围。
4. Agent Harness 负责约束模型，不把正确性寄托在一段超长 Prompt 上。
5. 所有外部模型协议必须使用 SSE 流式传输，供应商推理、正文、用量和终止事件分别投影。
6. 所有关键运行状态必须可持久化、恢复和回放。
7. Provider、模型、Agent、Harness、Skill、规则和交付通道分别扩展。
8. Console 是第一个适配器，不是应用核心。
9. Agent 必须观察公开事件、维护主观信念并执行角色策略，不能退化为只填写当前动作。

## 3. 推荐阅读顺序

1. [目标与 Harness 思想](01-目标与Harness思想.md)
2. [总体架构与模块边界](02-总体架构与模块边界.md)
3. [游戏领域与规则扩展](03-游戏领域与规则扩展.md)
4. [GameCoordinator 与动作收集规范](12-GameCoordinator与动作收集.md)
5. [Agent Harness 运行时](04-Agent-Harness运行时.md)
6. [战略认知与社会博弈规范](10-战略认知与社会博弈.md)
7. [战略智能演进与高级社会博弈](15-战略智能演进与高级社会博弈.md)
8. [战略智能升级实施计划](16-战略智能升级实施计划.md)
9. [模型协议与配置体系](05-模型协议与配置体系.md)
10. [编排、持久化与交付通道](06-编排持久化与交付通道.md)
11. [持久化存储设计与 Phoenix 借鉴](11-持久化存储设计与Phoenix借鉴.md)
12. [安全、审计、可观测性与评测](07-安全审计可观测性与评测.md)
13. [实施路线图与学习实验](08-实施路线图与学习实验.md)
14. [完整配置示例](09-完整配置示例.md)
15. [v2 可执行实现规格](13-v2可执行实现规格.md)
16. [v2 评审修复实施规格](14-v2评审修复实施规格.md)

## 4. 技术基线

| 项目 | 设计选择 |
| --- | --- |
| Java | Java 21 |
| 构建 | Maven 3.9+ 多模块 |
| Spring Boot | 4.1.x |
| Spring AI | 2.0.x，可选适配器 |
| 默认模型协议 | OpenAI Responses API |
| 持久化 | 本地默认 SQLite；服务器可切换 PostgreSQL；不以 H2 作为 SQLite 替代品 |
| Schema 迁移 | Flyway |
| Console | Spring Shell 或自有命令循环，优先保持交付层轻量 |
| Web | Spring MVC + SSE；未来可增加 WebSocket |
| 配置 | YAML + Spring Config Data + 外部 secrets 引用 |
| 可观测性 | Micrometer Observation / OpenTelemetry |

上述版本是 2026-08-07 的设计基线。Spring AI 官方文档显示 2.0.x 支持 Spring Boot 4.0.x 与 4.1.x。项目仍应通过自有 SPI 隔离版本变化。

## 5. 术语

| 术语 | 本项目中的含义 |
| --- | --- |
| Agent | 具有身份、目标、策略、记忆、工具、模型与执行约束的玩家运行单元 |
| Harness | 在模型外部控制上下文、能力、预算、循环、校验、重试和审计的工程系统 |
| Skill | 面向特定任务或阶段的可组合行为说明与能力声明 |
| Tool | 模型可以结构化调用、由宿主执行的受控能力 |
| Provider | OpenAI、Anthropic 等模型服务提供方或兼容服务 |
| Model Profile | 某个模型及其能力、参数和 Provider 绑定 |
| Agent Profile | 某个玩家 Agent 的人格、模型、Harness、Skill 与记忆配置 |
| Belief State | Agent 基于可见证据维护的主观角色概率和矛盾判断，不是游戏真相 |
| Strategy State | Agent 跨回合维护的目标、公开承诺、关系立场和可选伪装叙事 |
| Rule Pack | 可版本化的规则、阶段、角色、可见性与开局模板集合 |
| Player View | 从权威状态投影出的某玩家可见信息 |
| Proposed Action | 玩家控制器提出、尚未被规则引擎接受的动作 |
| Action Batch | 基于同一冻结状态收集一个或多个玩家候选动作的持久化批次 |
| GameCoordinator | 确定性推进游戏、调度控制器并提交动作批次的应用服务 |
| Domain Event | 规则引擎接受命令后产生的权威事实 |

## 6. 旧项目参考结论

旧项目 `D:\Code\Toy\avalon-ai` 中值得保留的方向包括：

- Maven 多模块与领域、运行时、Agent、持久化分层。
- 角色、规则、开局模板和模型档案配置化。
- `PlayerController` 对 LLM、真人和脚本玩家的多态抽象。
- Console-first、事件记录、审计、暂停与恢复。
- 按座位冻结模型配置，避免运行时配置漂移。

需要重新设计的部分包括：

- 用完整 Harness Pipeline 替代 `PromptBuilder + ResponseParser` 中心架构。
- 默认接入从 Chat Completions 升级为 Responses API。
- 游戏状态推进只能由领域规则引擎完成，运行时不重复实现规则。
- Memory 由 Harness 策略管理，而不是只接受模型返回的更新字段。
- Skill、Tool、上下文预算、失败策略和 Provider 能力成为一等配置。
- 增加增量公开观察、Belief State、角色策略、受控欺骗和多轮讨论协议。
- Human 等待态、REST/SSE 和未来 GUI 从第一版就共享应用用例边界。

## 7. 不在设计目标中的内容

- 不追求让 LLM 生成或修改游戏规则代码。
- 不允许模型直接读取完整游戏聚合或其他玩家私有状态。
- 不把模型隐藏思维链作为产品契约；Provider 主动返回的 reasoning 仅进入受控管理员调试流，默认不持久化全文。
- 不在第一阶段拆分微服务。
- 不为每个模型厂商复制一套业务编排逻辑。
- 不依赖某个 Provider 的持久会话才能恢复游戏。

## 8. 参考资料

- [OpenAI: Migrate to the Responses API](https://developers.openai.com/api/docs/guides/migrate-to-responses)
- [OpenAI: Conversation state](https://developers.openai.com/api/docs/guides/conversation-state)
- [Spring AI Reference](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
