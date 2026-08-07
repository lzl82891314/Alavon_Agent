# Avalon Agent Platform

这是一个用于学习和实践 Agent Engineering、Harness Engineering、多模型编排与可扩展游戏运行时的 Spring 项目。

当前仓库已实现可运行的控制台游戏基础、规则配置、模型随机池和协议 Adapter 层；更完整的 Harness、工具调用、GUI 和多人规则包仍处于后续演进范围。设计与实施入口：

- [设计文档总览](docs/design/README.md)
- [实施路线图](docs/design/08-实施路线图与学习实验.md)
- [完整配置示例](docs/design/09-完整配置示例.md)
- [战略认知与社会博弈规范](docs/design/10-战略认知与社会博弈.md)
- [GameCoordinator 与动作收集规范](docs/design/12-GameCoordinator与动作收集.md)
- [持久化存储设计与 Phoenix 借鉴](docs/design/11-持久化存储设计与Phoenix借鉴.md)

项目默认以经典 5 人阿瓦隆为第一个规则包，但架构支持扩展人数、角色、规则、LLM Provider、Agent Harness、真人玩家、Console、REST/SSE 和未来 GUI。

模型协议当前由 `protocol` 显式选择，而不是按厂商名称猜测：支持 `OPENAI_RESPONSES`、`OPENAI_COMPATIBLE_CHAT` 和原生 `ANTHROPIC_MESSAGES`。Claude 的默认档案使用 Anthropic Messages API；只有明确配置 `OPENAI_COMPATIBLE_CHAT` 的模型才会调用 `/chat/completions`。详见[模型协议与配置体系](docs/design/05-模型协议与配置体系.md)。
