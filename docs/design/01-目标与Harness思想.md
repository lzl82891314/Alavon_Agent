# 01 - 目标与 Harness 思想

## 1. 项目要解决的问题

普通的 LLM 游戏 Demo 往往采用如下模式：

```text
拼接规则 + 历史记录 + 玩家身份
-> 调用模型
-> 从自然语言中猜测模型想做什么
-> 修改游戏状态
```

这种实现可以快速得到演示效果，但很难回答以下工程问题：

- 模型为什么只能看到这些信息？
- 模型输出非法动作时谁负责修正？
- 更换模型后 Prompt 是否仍然有效？
- 上下文超长时哪些信息应该保留？
- 模型超时、限流、输出截断时游戏如何恢复？
- 如何证明某次失败来自模型、规则、上下文还是协议适配器？
- 如何在同一局中混用不同厂商、真人和脚本玩家？
- 如何重放一局游戏而不再次调用模型？

Harness Engineering 的价值，就是把这些问题从 Prompt 文本中移到可设计、可配置、可验证的宿主系统中。

## 2. Prompt Engineering 与 Harness Engineering

Prompt Engineering 关注“对模型说什么”。Harness Engineering 关注“模型在什么环境中工作，以及宿主如何控制整个执行过程”。

| 维度 | Prompt Engineering | Harness Engineering |
| --- | --- | --- |
| 指令 | 一段或多段文本 | 版本化指令、阶段策略与动态装配 |
| 上下文 | 尽量塞入历史 | 选择、投影、压缩、预算和来源追踪 |
| 输出 | 依赖模型遵守格式 | Schema、工具调用、解析和领域校验 |
| 错误 | 再写一句“请重试” | 分类、有限重试、修复请求、暂停和降级 |
| 能力 | Prompt 描述“你可以做什么” | Tool Registry 真正决定可执行能力 |
| 记忆 | 把历史继续传入 | 工作记忆、情节记忆、摘要与持久化策略 |
| 安全 | 告诉模型不要泄密 | 服务端构造私有视图并实施权限隔离 |
| 评测 | 看最终回答是否像样 | 对输入、决策、动作、成本和结果全链路评测 |

本项目仍然需要高质量 Prompt，但 Prompt 只是 Harness 的一个部件。

## 3. Agent 的工程定义

本项目中的 Agent 不是某次模型调用，也不是模型名称。一个可运行 Agent 由以下要素组成：

```text
Agent = Identity
      + Objective
      + Model Profile
      + Harness Profile
      + Skill Set
      + Tool Policy
      + Memory Policy
      + Budget Policy
      + Failure Policy
```

同一个模型可以创建多个行为不同的 Agent；同一个 Agent 也可以在不改变身份和策略的情况下更换模型。

### 3.1 Identity

描述 Agent 在游戏中的稳定身份，但不包含游戏随机分配的秘密角色。

示例：

- 显示名称
- 行为风格
- 表达长度偏好
- 风险偏好
- 是否倾向主动领导
- 是否倾向基于证据而非直觉发言

### 3.2 Objective

目标由游戏规则和当前角色共同生成。目标不是静态 Prompt 中写死的“你要赢”，而是 Harness 在每回合根据权威状态装配：

- 当前阵营胜利目标
- 当前阶段必须完成的动作
- 当前角色的特殊目标
- 禁止泄露的私有事实

### 3.3 Model Profile

描述模型连接和能力，不描述游戏人格：

- Provider
- 模型标识
- 推理能力
- Structured Outputs 能力
- Tool Calling 能力
- 上下文窗口
- 最大输出
- 默认超时和参数

### 3.4 Harness Profile

决定 Agent 如何运行：

- 上下文装配策略
- 记忆检索与压缩策略
- Skill 解析策略
- Tool 暴露策略
- 最大模型调用次数
- 校验与修复循环
- 超时、重试、降级和暂停策略
- 审计级别

## 4. 游戏规则也是一种 Harness

从 Agent 视角看，阿瓦隆游戏本身就是一个强 Harness：

- 阶段机限制当前可做的事情。
- 角色决定可见信息和目标。
- 投票、组队和任务动作具有固定 Schema。
- 规则引擎验证动作，不相信自然语言声明。
- 公共事件构成所有玩家共享的外部环境。
- 私有视图构成每个玩家不同的观察空间。
- 胜负条件提供不可由模型篡改的反馈。

因此系统存在两层 Harness：

1. `Game Harness`：规则、阶段、动作、可见性、胜负。
2. `Agent Harness`：上下文、Skill、Tool、Memory、模型调用、校验与审计。

两层之间通过 `PlayerTurnContext` 和 `ProposedAction` 交互。

## 5. 确定性内核与非确定性边缘

推荐把系统划分为：

```text
确定性内核：
  规则校验、状态变化、角色知识、胜负判断、事件投影

非确定性边缘：
  LLM 推理、自然语言发言、策略判断、记忆建议
```

非确定性边缘永远不能直接修改内核状态。

错误示例：

```java
game.setMissionSucceeded(modelSaysMissionSucceeded);
```

正确方向：

```java
MissionVote proposed = controller.propose(context);
List<DomainEvent> events = ruleEngine.decide(state, proposed);
```

## 6. Harness 的控制循环

一个 Agent 回合不是“一次 HTTP 请求”，而是一个有界执行循环：

```text
1. 读取权威游戏状态
2. 构建当前玩家视图
3. 解析当前阶段能力
4. 装配上下文与记忆
5. 选择模型和参数
6. 调用模型
7. 解析结构化结果或工具调用
8. 执行协议校验
9. 执行领域动作校验
10. 必要时构造修复上下文并有限重试
11. 返回合法 ProposedAction 或明确失败
12. 记录 Trace、使用量、审计摘要和记忆变更
```

每一步都必须有明确输入、输出和失败分类。

## 7. Skill 的定位

Skill 是可组合的行为知识，不是 Java 插件的同义词。一个 Skill 至少包含：

- `skillId` 和版本
- 适用阶段或动作类型
- 指令片段
- 可使用的工具声明
- 可读取的上下文片段声明
- 所需输出字段
- 冲突与优先级信息

示例 Skill：

- `public-discussion-basic`
- `team-proposal-analysis`
- `team-vote-analysis`
- `mission-choice-good`
- `mission-choice-evil`
- `assassination-deduction`
- `concise-speaker`
- `evidence-tracker`

Skill 不能绕过规则引擎，也不能扩大玩家可见信息。

## 8. Tool 的定位

阿瓦隆中的工具不一定是外部搜索。更有教学价值的是提供小型、确定性、可审计的认知工具：

- 查询公开事件时间线
- 统计玩家投票历史
- 比较玩家发言与投票的一致性
- 查询当前合法动作 Schema
- 读取 Agent 自己的结构化记忆
- 计算候选队伍组合

Tool 的结果必须来自该玩家有权访问的数据投影。即使模型请求了不存在或越权的参数，工具执行层也必须拒绝。

## 9. Memory 的定位

Memory 不是把所有历史原文放入 Prompt。设计上区分：

| 类型 | 内容 | 生命周期 |
| --- | --- | --- |
| Working Memory | 当前回合推理所需上下文 | 单次 Harness 执行 |
| Episodic Memory | 关键事件、承诺、怀疑变化 | 单局游戏 |
| Semantic Memory | 稳定策略与角色知识 | Agent Profile 版本内 |
| Provider Context | Provider 保存的响应链 | 可选缓存，不是权威状态 |

模型可以提出记忆更新建议，但最终写入由 `MemoryPolicy` 过滤、归一化和限制容量。

## 10. 不保存原始思维链

系统需要可审计，但不需要要求模型输出或保存完整 Chain of Thought。

审计记录保存：

- 决策目标
- 简短理由摘要
- 使用的公开证据引用
- 置信度
- 最终动作
- 校验失败和重试信息
- 模型、参数、延迟和 Token 用量

这既能解释行为，也避免把不可控的内部推理文本当成产品契约。

## 11. 项目成功标准

当以下条件成立时，项目才算真正体现 Harness Engineering：

- 更换模型不需要修改规则层。
- 新增角色不需要修改模型 Provider。
- 新增 GUI 不需要复制游戏推进逻辑。
- 非法输出不会污染游戏状态。
- 任意时刻可以说明某玩家看到了哪些信息以及来源。
- 任意一局可以从事件和快照恢复，而不依赖 Provider 会话。
- 可以比较两个 Harness Profile，而不只比较两个 Prompt。
- 可以明确区分模型失败、协议失败、校验失败和规则拒绝。
- Agent 能观察其他玩家的公开发言、提案、逐人投票和任务结果。
- Agent 的主观信念、公开承诺和角色策略能够跨回合持续演进。
- 梅林隐藏、邪恶误导、派西维尔保护和刺客识别具有可区分的行为表现。
- 战略 Harness 通过固定场景和基线对照证明优于“最小合法 JSON”模式。

完整的智能行为约束见 [战略认知与社会博弈规范](10-战略认知与社会博弈.md)。
