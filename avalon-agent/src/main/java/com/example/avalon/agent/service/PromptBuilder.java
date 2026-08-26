package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/** Renders a strategic decision contract, not a role-play form. */
@Component
public final class PromptBuilder {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public String build(AgentTurnRequest request) {
        String prompt = """
                你是阿瓦隆对局中的独立战略 Agent。目标不是完成流程，而是在信息不完全和其他玩家可能欺骗的条件下，为自己的阵营争取胜利。

                ## 权限和事实边界
                - 只能使用本请求中的私有知识、公开快照、公开观察增量和自己的既有认知状态。
                - WORLD_FACT 是规则引擎确认的事实；PUBLIC_CLAIM 只是某人的公开主张，可能错误或故意欺骗。
                - 不得假设你能读取其他玩家的私有记忆、身份、任务票或模型响应。
                - 可以保留 UNKNOWN。没有新证据时不要大幅改变概率。
                - roleBeliefs 必须忠实表达私有阵营知识：自己的阵营以及 visiblePlayers 中 camp 明确的玩家，GOOD 必须写 0.0，EVIL 必须写 1.0，不得用中间概率弱化确定知识。
                - 游戏内隐瞒和误导仅在角色策略允许的 deceptionIntent 范围内合法。

                ## 当前身份与规则
                gameId: %s
                playerId: %s
                seatNo: %s
                roleId: %s
                roundNo: %s
                phase: %s
                rules: %s
                allowedActions: %s
                privateKnowledge: %s

                ## 当前公共状态
                %s

                ## 自上次成功行动后的完整可见增量
                sequenceRange: (%d, %d]
                %s

                ## 你的私有跨回合认知
                %s

                ## 角色策略与差异化参数
                %s

                ## 当前讨论指令
                %s

                ## 决策要求
                1. 先比较新事件与既有信念、承诺和叙事，识别支持证据、反证和矛盾。
                2. 如果提供 memoryUpdate，roleBeliefs 中的值表示玩家属于邪恶阵营的概率，范围必须为 0 到 1。没有对应 beliefEvidenceReferences 时，单次相对既有值最多变化 0.05；有对应的、直接提及该玩家的可见证据时最多变化 0.25；首次出现的玩家以 0.5 为基线，最多变化 0.15。超过这些幅度必须拆分到后续回合，不能一次完成。
                3. 如果提供 strategyState，请记录 mode、objective、unresolvedQuestions、publicCommitments、coverStory、deceptionIntent 和 consistencyRisks。MERLIN 还必须记录数值型 exposureRisk，范围为 0 到 1。
                4. 如果提供 communicationPlan，请说明 speechAct、desiredAudienceBeliefs、evidenceToMention、evidenceToWithhold 和 publicMessage。
                5. 公开表达必须服务于策略；不要复述规则、回合进度或泛泛地说“继续观察”。
                6. 如果当前是 TARGETED_RESPONSES，必须回答讨论指令指定的质疑；如果是 LEADER_SYNTHESIS，必须综合争议后给出队伍判断。
                7. 优先使用宿主提供的 voteEvidence、teamCandidates、missionConstraints 和 contradictionCandidates；至少比较两个可行队伍、投票或沟通方案，并说明选择依据。
                8. 依据角色策略选择当前模式和风险档位；刺客从非刺杀阶段持续更新梅林候选，不要把刺杀只当作最终失败后的流程动作。
                9. audiencePlan 和 highRiskRoleClaim 只是候选计划。高风险身份声明必须同时评估目标、预期反应、风险和退出叙事，不得因为存在候选就强制执行。
                10. action.speechText、communicationPlan.publicMessage、publicSpeech 和 privateThought 必须使用简体中文，不得写英文句子；P1 之类的玩家编号和 JSON 契约规定的英文枚举不受此限制。
                11. 不输出原始思维链。privateThought 只写一句简短的中文策略摘要；只输出下面的结构化决策产物和动作。

                ## 输出契约
                只返回一个 JSON 对象：
                {
                  "memoryUpdate": {
                    "roleBeliefs": {"playerId": 0.0},
                    "evidenceReferences": [0],
                    "beliefEvidenceReferences": {"playerId": [0]},
                    "strategyState": {
                      "mode": "...",
                      "objective": "...",
                      "unresolvedQuestions": [],
                      "coverStory": {},
                      "deceptionIntent": "NONE",
                      "exposureRisk": 0.0,
                      "consistencyRisks": []
                    },
                    "communicationPlan": {
                      "speechAct": "...",
                      "desiredAudienceBeliefs": {},
                      "evidenceToMention": [],
                      "evidenceToWithhold": [],
                      "publicMessage": "..."
                    },
                    "strategyMode": "...",
                    "lastSummary": "..."
                  },
                  "action": %s,
                  "publicSpeech": "仅在公开表达适用时填写，并与 action.speechText 一致",
                  "privateThought": "一句简短的中文策略摘要"
                }

                action 是唯一必填字段。memoryUpdate 是可选的私有认知草稿；无法保证其字段合法时请省略，不要牺牲 action 的正确性。
                如果提供 memoryUpdate，evidenceReferences 只能引用 sequenceRange 内或既有记忆中存在的公开事件。
                beliefEvidenceReferences 必须按玩家绑定与该玩家直接相关的可见事件；全局 evidenceReferences 不能替代逐玩家绑定。
                """.formatted(
                request.getGameId(), request.getPlayerId(), request.getSeatNo(), request.getRoleId(),
                request.getRoundNo(), request.getPhase(), request.getRulesSummary(), json(request.getAllowedActions()),
                json(request.getPrivateKnowledge()), json(request.getPublicState()),
                request.getObservationFromSequence(), request.getObservationToSequence(), json(request.getObservationDelta()),
                json(request.getMemory()), json(request.getStrategyContext()), json(request.getDiscussionDirective()),
                actionContract(request.getAllowedActions())).strip()
                .replace("publicCommitments", "accepted commitments are host-owned");
        if (requiresCompactPrivateAction(request.getAllowedActions())) {
            prompt += """

                    ## 当前阶段紧凑输出
                    这是私密原子动作。最终 JSON 只包含 action；省略 memoryUpdate、publicSpeech、privateThought 和 auditReason。
                    """;
        }
        return prompt;
    }

    private String actionContract(List<String> allowedActions) {
        String type = allowedActions == null || allowedActions.size() != 1 ? null : allowedActions.get(0);
        if (type == null) return "{\"actionType\": \"one of allowedActions\"}";
        return switch (type) {
            case "PUBLIC_SPEECH" -> "{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"...\",\"speechAct\":\"allowed speech act\",\"mentions\":[],\"replyToEventSequences\":[],\"supersedesSequence\":null}";
            case "TEAM_PROPOSAL" -> "{\"actionType\":\"TEAM_PROPOSAL\",\"selectedPlayerIds\":[\"exact required team size\"]}";
            case "TEAM_VOTE" -> "{\"actionType\":\"TEAM_VOTE\",\"vote\":\"APPROVE or REJECT\"}";
            case "MISSION_ACTION" -> "{\"actionType\":\"MISSION_ACTION\",\"choice\":\"SUCCESS or FAIL when role permits\"}";
            case "ASSASSINATION" -> "{\"actionType\":\"ASSASSINATION\",\"targetPlayerId\":\"eligible player\"}";
            default -> "{\"actionType\":\"" + type + "\"}";
        };
    }

    private boolean requiresCompactPrivateAction(List<String> allowedActions) {
        return allowedActions != null
                && allowedActions.size() == 1
                && ("TEAM_VOTE".equals(allowedActions.get(0))
                || "MISSION_ACTION".equals(allowedActions.get(0)));
    }

    private String json(Object value) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot render strategic prompt context", exception);
        }
    }
}
