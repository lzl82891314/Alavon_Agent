package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.OpenAiCompatibleSupport;
import com.example.avalon.agent.model.AgentTurnRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class PromptBuilder {
    public String build(AgentTurnRequest request) {
        StringBuilder builder = new StringBuilder("""
                你正在扮演一名阿瓦隆玩家。
                请严格代入当前玩家身份，只依据你可见的信息行动。
                游戏ID：%s
                轮次：%s
                阶段：%s
                玩家：%s（座位 %s，身份 %s）
                可执行动作：%s
                规则摘要：%s
                私有情报：%s
                记忆：%s
                公开局面：%s
                输出 schema 版本：%s
                只返回一个 JSON 对象，不要输出 Markdown、代码块、<think>、项目符号或任何解释文字。
                最终输出的第一个字符必须是 {，最后一个字符必须是 }。
                优先返回最小合法 JSON，并把第一层键按 action、publicSpeech、privateThought、auditReason、memoryUpdate 的顺序写出。
                action 必填，且必须是当前阶段合法的结构化动作 JSON。
                publicSpeech 只有在当前阶段需要公开发言时才提供；如果提供，只写 1 到 2 句简短中文。
                privateThought 可以省略或写 null；如果提供，只写一句极短中文，不要长篇分析。
                auditReason 和 memoryUpdate 默认省略；只有在确有必要时才提供。
                如果提供 auditReason，它必须是 JSON 对象，字段只允许 goal、reasonSummary、confidence、beliefs。
                如果提供 memoryUpdate，它必须是 JSON 对象，字段只允许 suspicionDelta、trustDelta、observationsToAdd、commitmentsToAdd、inferredFactsToAdd、strategyMode、lastSummary。
                关于私有知识的强规则：
                - 只有 exactRoleId 明确告诉你的身份，才能当作确定事实写出来。
                - 如果某位玩家只有 candidateRoleIds，你只知道他属于候选集合，不知道真实身份。
                - 对 candidateRoleIds 的描述必须使用“怀疑 / 可能 / 更像 / 倾向 / 猜测”等不确定表达。
                - 绝不能在 privateThought 或 auditReason.reasonSummary 里写出“P5是梅林”“P3就是莫甘娜”这类确定断言。
                """.formatted(
                request.getGameId(),
                request.getRoundNo(),
                request.getPhase(),
                request.getPlayerId(),
                request.getSeatNo(),
                request.getRoleId(),
                request.getAllowedActions(),
                request.getRulesSummary(),
                privateKnowledgeText(request.getPrivateKnowledge()),
                request.getMemory(),
                request.getPublicState(),
                request.getOutputSchemaVersion()
        ).strip());
        builder.append(System.lineSeparator())
                .append("最小合法示例：")
                .append(System.lineSeparator())
                .append(exampleJson(request.getAllowedActions()))
                .append(System.lineSeparator())
                .append("如果确实需要提供 memoryUpdate，最小合法示例：")
                .append(System.lineSeparator())
                .append("{\"action\":{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"我先给出公开看法。\"},\"publicSpeech\":\"我先给出公开看法。\",\"memoryUpdate\":{\"observationsToAdd\":[\"记录一条新观察\"],\"strategyMode\":\"BALANCED\",\"lastSummary\":\"保持低风险验证。\"}}");
        if ("minimax".equals(OpenAiCompatibleSupport.providerId(request.getProvider()))) {
            builder.append(System.lineSeparator())
                    .append("""
                            兼容要求：
                            - 不要输出项目符号
                            - action.actionType 只能从当前 allowedActions 中选择
                            """.strip());
        }
        return builder.toString();
    }

    private String privateKnowledgeText(Map<String, Object> privateKnowledge) {
        if (privateKnowledge == null || privateKnowledge.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        String camp = stringValue(privateKnowledge.get("camp"));
        if (camp != null) {
            builder.append(System.lineSeparator())
                    .append("阵营：")
                    .append(camp);
        }
        Object rawVisiblePlayers = privateKnowledge.get("visiblePlayers");
        if (rawVisiblePlayers instanceof Collection<?> visiblePlayers && !visiblePlayers.isEmpty()) {
            builder.append(System.lineSeparator()).append("可见玩家：");
            for (Object rawVisiblePlayer : visiblePlayers) {
                if (!(rawVisiblePlayer instanceof Map<?, ?> visiblePlayer)) {
                    continue;
                }
                String playerId = stringValue(visiblePlayer.get("playerId"));
                if (playerId == null) {
                    continue;
                }
                String displayName = stringValue(visiblePlayer.get("displayName"));
                String exactRoleId = stringValue(visiblePlayer.get("exactRoleId"));
                List<String> candidateRoleIds = stringList(visiblePlayer.get("candidateRoleIds"));
                builder.append(System.lineSeparator())
                        .append("- ")
                        .append(playerLabel(playerId, displayName))
                        .append("：");
                if (exactRoleId != null) {
                    builder.append("已知真实身份 ")
                            .append(exactRoleId)
                            .append("。");
                    continue;
                }
                if (!candidateRoleIds.isEmpty()) {
                    builder.append("候选身份 ")
                            .append(candidateRoleIds)
                            .append("。这只代表候选集合，不代表你已知真实身份。");
                    continue;
                }
                String visibleCamp = stringValue(visiblePlayer.get("camp"));
                if (visibleCamp != null) {
                    builder.append("已知阵营 ")
                            .append(visibleCamp)
                            .append("。");
                    continue;
                }
                builder.append("没有额外身份确定信息。");
            }
        }
        List<String> notes = stringList(privateKnowledge.get("notes"));
        if (!notes.isEmpty()) {
            builder.append(System.lineSeparator()).append("备注：");
            for (String note : notes) {
                builder.append(System.lineSeparator())
                        .append("- ")
                        .append(note);
            }
        }
        return builder.length() == 0 ? "无" : builder.toString().strip();
    }

    private String exampleJson(List<String> allowedActions) {
        String primaryAction = allowedActions == null || allowedActions.isEmpty()
                ? "PUBLIC_SPEECH"
                : allowedActions.get(0);
        return switch (primaryAction) {
            case "TEAM_PROPOSAL" -> """
                    {"action":{"actionType":"TEAM_PROPOSAL","selectedPlayerIds":["P1","P2"]},"publicSpeech":"我先提一个可验证的队伍。","privateThought":"先做一轮低风险验证。"}
                    """.strip();
            case "TEAM_VOTE" -> """
                    {"action":{"actionType":"TEAM_VOTE","vote":"APPROVE"},"publicSpeech":"我暂时支持这支队伍。","privateThought":"先看这一轮投票。"}
                    """.strip();
            case "MISSION_ACTION" -> """
                    {"action":{"actionType":"MISSION_ACTION","choice":"SUCCESS"},"privateThought":"先执行当前任务动作。"}
                    """.strip();
            case "ASSASSINATION" -> """
                    {"action":{"actionType":"ASSASSINATION","targetPlayerId":"P1"},"publicSpeech":"我现在给出刺杀目标。","privateThought":"优先锁定最像梅林的玩家。"}
                    """.strip();
            default -> """
                    {"action":{"actionType":"PUBLIC_SPEECH","speechText":"我先给出公开看法。"},"publicSpeech":"我先给出公开看法。","privateThought":"先收集一轮信息。"}
                    """.strip();
        };
    }

    private String playerLabel(String playerId, String displayName) {
        if (displayName == null || Objects.equals(displayName, playerId)) {
            return playerId;
        }
        return playerId + "/" + displayName;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .map(this::stringValue)
                .filter(Objects::nonNull)
                .toList();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
