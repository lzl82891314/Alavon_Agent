package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/** Renders a strategic decision contract, not a role-play form. */
@Component
public final class PromptBuilder {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public String buildToolCalling(AgentTurnRequest request) {
        String prompt = """
                你是阿瓦隆对局中的独立战略 Agent。你通过只读工具主动收集证据、读取自己的既有认知并比较候选，最后为自己的阵营提交唯一合法动作。

                ## 权限和事实边界
                - 只能使用本提示中的身份、规则、公共快照，以及宿主提供的只读工具结果。
                - WORLD_FACT 是规则引擎确认的事实；PUBLIC_CLAIM 只是公开主张，可能错误或故意欺骗。
                - 不得要求或猜测访问其他玩家的私有记忆、身份、任务票或模型响应。
                - 工具结果是受限证据或宿主计算的候选分析，不会直接修改游戏状态或正式记忆。
                - 可以保留 UNKNOWN。没有新证据时不要大幅改变概率。
                - 角色策略允许时可以隐瞒或误导，但不得公开泄露 privateKnowledge 中的秘密身份知识。

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

                ## 当前公共快照
                %s

                ## 当前讨论指令
                %s

                ## 可查询范围
                publicSequenceRange: (%d, %d]
                历史公开事件、投票证据、私有跨回合记忆、可能世界、预测反馈、角色策略、候选行动和受众计划没有内联到本提示中；按需调用工具获取。

                ## Agent Loop 策略
                1. 先判断当前动作需要哪些证据，不要为了调用而调用工具；只能调用本次 Provider tools 列表实际提供的工具。
                2. 需要延续既有信念、可能世界、预测或公开承诺时，调用 get_my_memory。
                3. 需要核对公开事实、发言、投票、任务结果或矛盾时，调用 get_public_timeline、get_vote_history 或 compare_player_consistency。
                4. 需要组队或评价队伍时，调用 evaluate_team_combinations，比较多个世界下的阵营价值、信息增益、暴露成本、承诺成本和后续观察点。
                5. 不确定动作字段时调用 list_legal_actions。Tool Call 不能代替最终游戏动作。
                6. 根据工具证据保留多个仍可区分的世界；对既有预测给出 SUPPORTED、CONTRADICTED、INCONCLUSIVE 或 EXPIRED 反馈。
                7. 形成角色目标、风险档位、关键受众、公开承诺、指控回应和退出叙事；高风险身份声明必须评估暴露成本。
                8. 公开表达必须服务于策略，不复述规则或泛泛地说“继续观察”。
                9. 最终 action 必须严格属于 allowedActions，并遵守讨论指令和规则。
                10. 不输出原始思维链；privateThought 只写一句简短中文策略摘要。
                11. roleBeliefs 表示邪恶概率；确定阵营必须为 0.0 或 1.0。没有逐玩家证据时相对既有值最多变化 0.05，有直接证据时最多变化 0.25，首次玩家相对 0.5 最多变化 0.15。
                12. 更新既有 worldHypotheses 和 activePredictions 时保留其 ID 与可见 sequence 依据，不得因当前动作选择静默改写历史。

                ## 最终输出契约
                工具调用结束后，只返回一个 json 对象：
                {
                  "memoryUpdate": {
                    "worldHypotheses": [{"worldId":"...","roleAssignments":{},"constraints":[],"priorWeight":0.0,"posteriorWeight":0.0,"supportingEvidenceReferences":[],"opposingEvidenceReferences":[],"predictions":[],"updatedAtSequence":0}],
                    "activePredictions": [{"predictionId":"...","worldId":"...","subjectPlayerId":"...","situation":"...","expectedBehaviors":[],"discriminatingObservationReferences":[],"status":"PENDING or SUPPORTED or CONTRADICTED or INCONCLUSIVE or EXPIRED","validThroughSequence":0}],
                    "actionAssessments": [{"candidateId":"...","actionType":"...","action":{},"worldOutcomes":{},"expectedCampValue":0.0,"expectedInformationGain":0.0,"exposureCost":0.0,"commitmentCost":0.0,"executionRisk":0.0,"evidenceReferences":[],"followUpObservationReferences":[]}],
                    "roleBeliefs": {"playerId":0.0},
                    "evidenceReferences": [0],
                    "beliefEvidenceReferences": {"playerId":[0]},
                    "strategyState": {"mode":"...","objective":"...","unresolvedQuestions":[],"coverStory":{},"deceptionIntent":"NONE","exposureRisk":0.0,"consistencyRisks":[]},
                    "communicationPlan": {"speechAct":"...","desiredAudienceBeliefs":{},"evidenceToMention":[],"evidenceToWithhold":[],"targetAudience":[],"expectedReactions":[],"observedAudienceFeedback":[],"publicMessage":"..."},
                    "strategyMode": "...",
                    "lastSummary": "..."
                  },
                  "action": %s,
                  "publicSpeech": "仅在公开表达适用时填写，并与 action.speechText 一致",
                  "privateThought": "一句简短的中文策略摘要"
                }

                action 是唯一必填字段。memoryUpdate 是基于工具证据形成的可选私有认知草稿；无法保证合法时应省略，不得牺牲 action 正确性。
                roleBeliefs 必须忠实保留 privateKnowledge 中已确定的阵营知识。证据引用只能使用工具返回或既有记忆中存在的可见 sequence。
                action.speechText、communicationPlan.publicMessage、publicSpeech 和 privateThought 必须使用简体中文；玩家编号与 JSON 枚举除外。
                """.formatted(
                request.getGameId(), request.getPlayerId(), request.getSeatNo(), request.getRoleId(),
                request.getRoundNo(), request.getPhase(), request.getRulesSummary(), json(request.getAllowedActions()),
                json(request.getPrivateKnowledge()), json(request.getPublicState()),
                json(request.getDiscussionDirective()), request.getObservationFromSequence(),
                request.getObservationToSequence(), actionContract(request.getAllowedActions())).strip();
        if (requiresCompactPrivateAction(request.getAllowedActions())) {
            prompt += """

                    ## 当前阶段紧凑输出
                    这是私密原子动作。最终 json 只包含 action；省略 memoryUpdate、publicSpeech、privateThought 和 auditReason。
                    """;
        }
        return prompt;
    }

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
                3. 如果提供 worldHypotheses，保留至少两个仍可区分的解释（当候选不止一个时），每个世界只使用可见约束和序号证据；不得因本轮动作选择而静默改写既有世界。
                4. 如果提供 activePredictions，逐项保留原 predictionId，并只将其标为 SUPPORTED、CONTRADICTED、INCONCLUSIVE 或 EXPIRED；新预测必须绑定 worldId、后续公开观察点和有效序号窗口。
                5. 如果提供 actionAssessments，至少比较两个合法候选，写明各世界结果、阵营价值、信息增益、暴露成本、承诺成本、执行风险、证据序号和后续观察点；以角色策略的 objectiveWeights 和 riskBudget 进行排序，不得把固定队伍或玩家写成规则。
                6. 如果提供 strategyState，请记录 mode、objective、unresolvedQuestions、publicCommitments、coverStory、deceptionIntent 和 consistencyRisks。MERLIN 还必须记录数值型 exposureRisk，范围为 0 到 1。
                7. 如果提供 communicationPlan，请说明 speechAct、desiredAudienceBeliefs、evidenceToMention、evidenceToWithhold 和 publicMessage，并记录所选关键受众、预期反应和仅基于公开事件的实际反馈。
                8. 公开表达必须服务于策略；不要复述规则、回合进度或泛泛地说“继续观察”。
                9. 如果当前是 TARGETED_RESPONSES，必须回答讨论指令指定的质疑；如果是 LEADER_SYNTHESIS，必须综合争议后给出队伍判断。
                10. 若 audiencePlan 中提供 accusationResponsePlan，选择其一项候选回应策略：直接否认、证据反驳、局部承认、焦点转移或有理由沉默。回应必须引用指控序号、指出证据解释或替代解释；不得因被指控而默认身份结论成立。
                11. 优先使用宿主提供的 voteEvidence、teamCandidates、missionConstraints 和 contradictionCandidates；至少比较两个可行队伍、投票或沟通方案，并说明选择依据。
                12. 依据角色策略选择当前模式和风险档位；刺客从非刺杀阶段持续更新梅林候选，不要把刺杀只当作最终失败后的流程动作。
                13. audiencePlan 和 highRiskRoleClaim 只是候选计划。高风险身份声明必须同时评估目标、预期反应、风险和退出叙事，不得因为存在候选就强制执行。
                14. action.speechText、communicationPlan.publicMessage、publicSpeech 和 privateThought 必须使用简体中文，不得写英文句子；P1 之类的玩家编号和 JSON 契约规定的英文枚举不受此限制。
                15. 不输出原始思维链。privateThought 只写一句简短的中文策略摘要；只输出下面的结构化决策产物和动作。

                ## 输出契约
                只返回一个 json 对象：
                {
                    "memoryUpdate": {
                    "worldHypotheses": [{"worldId":"...","roleAssignments":{},"constraints":[],"priorWeight":0.0,"posteriorWeight":0.0,"supportingEvidenceReferences":[],"opposingEvidenceReferences":[],"predictions":[],"updatedAtSequence":0}],
                    "activePredictions": [{"predictionId":"...","worldId":"...","subjectPlayerId":"...","situation":"...","expectedBehaviors":[],"discriminatingObservationReferences":[],"status":"PENDING or SUPPORTED or CONTRADICTED or INCONCLUSIVE or EXPIRED","validThroughSequence":0}],
                    "actionAssessments": [{"candidateId":"...","actionType":"...","action":{},"worldOutcomes":{},"expectedCampValue":0.0,"expectedInformationGain":0.0,"exposureCost":0.0,"commitmentCost":0.0,"executionRisk":0.0,"evidenceReferences":[],"followUpObservationReferences":[]}],
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
                      "targetAudience": [],
                      "expectedReactions": [],
                      "observedAudienceFeedback": [],
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
                    这是私密原子动作。最终 json 只包含 action；省略 memoryUpdate、publicSpeech、privateThought 和 auditReason。
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
