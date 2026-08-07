package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AuditReason;
import com.example.avalon.core.player.memory.VisiblePlayerInfo;
import com.example.avalon.core.game.model.PlayerTurnContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

final class PrivateKnowledgeExpressionValidator {
    private static final List<String> UNCERTAINTY_MARKERS = List.of(
            "可能", "也许", "怀疑", "猜", "猜测", "更像", "像", "倾向", "疑似", "大概",
            "或许", "不确定", "未必", "之一", "候选", "似乎", "估计", "推测", "概率",
            "大概率", "更可能", "如果", "若", "像是"
    );

    void validate(PlayerTurnContext context, AgentTurnResult result) {
        List<CandidateKnowledge> candidateKnowledge = candidateKnowledge(context);
        if (candidateKnowledge.isEmpty() || result == null) {
            return;
        }
        validateText("privateThought", result.getPrivateThought(), candidateKnowledge);
        AuditReason auditReason = result.getAuditReason();
        if (auditReason == null || auditReason.getReasonSummary() == null) {
            return;
        }
        for (String summary : auditReason.getReasonSummary()) {
            validateText("auditReason.reasonSummary", summary, candidateKnowledge);
        }
    }

    private List<CandidateKnowledge> candidateKnowledge(PlayerTurnContext context) {
        List<CandidateKnowledge> candidateKnowledge = new ArrayList<>();
        for (VisiblePlayerInfo visiblePlayer : context.privateView().knowledge().visiblePlayers()) {
            if (visiblePlayer.exactRoleId() != null || visiblePlayer.candidateRoleIds().isEmpty()) {
                continue;
            }
            Map<String, List<String>> aliasesByRole = new LinkedHashMap<>();
            for (String candidateRoleId : visiblePlayer.candidateRoleIds()) {
                aliasesByRole.put(candidateRoleId, roleAliases(candidateRoleId));
            }
            candidateKnowledge.add(new CandidateKnowledge(
                    visiblePlayer.playerId(),
                    playerAliases(visiblePlayer),
                    List.copyOf(visiblePlayer.candidateRoleIds()),
                    aliasesByRole
            ));
        }
        return candidateKnowledge;
    }

    private void validateText(String fieldName, String text, List<CandidateKnowledge> candidateKnowledge) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String clause : clauses(text)) {
            String trimmedClause = clause.trim();
            if (trimmedClause.isEmpty() || containsUncertaintyMarker(trimmedClause)) {
                continue;
            }
            for (CandidateKnowledge knowledge : candidateKnowledge) {
                for (String playerAlias : knowledge.playerAliases()) {
                    for (Map.Entry<String, List<String>> entry : knowledge.roleAliasesByRoleId().entrySet()) {
                        for (String roleAlias : entry.getValue()) {
                            if (!containsCertainRoleAssertion(trimmedClause, playerAlias, roleAlias)) {
                                continue;
                            }
                            String violationSummary = fieldName
                                    + "="
                                    + trimmedClause
                                    + " ("
                                    + knowledge.playerId()
                                    + " only has candidate roles "
                                    + knowledge.candidateRoleIds()
                                    + ")";
                            throw new CandidateKnowledgeAssertionException(
                                    "候选身份只能作为不确定推测，不能写成确定事实: " + violationSummary,
                                    violationSummary
                            );
                        }
                    }
                }
            }
        }
    }

    private boolean containsCertainRoleAssertion(String clause, String playerId, String roleAlias) {
        if (playerId == null || roleAlias == null) {
            return false;
        }
        return certaintyPattern(playerId, roleAlias).matcher(clause).find()
                || certaintyPattern(roleAlias, playerId).matcher(clause).find();
    }

    private Pattern certaintyPattern(String left, String right) {
        return Pattern.compile(Pattern.quote(left) + "\\s*(就?是|为)\\s*" + Pattern.quote(right), Pattern.CASE_INSENSITIVE);
    }

    private boolean containsUncertaintyMarker(String clause) {
        String normalized = clause.toLowerCase(Locale.ROOT);
        return UNCERTAINTY_MARKERS.stream()
                .map(marker -> marker.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private List<String> clauses(String text) {
        String[] rawClauses = text.split("[\\r\\n。！？；;，,]");
        List<String> clauses = new ArrayList<>();
        for (String rawClause : rawClauses) {
            String trimmed = rawClause == null ? null : rawClause.trim();
            if (trimmed != null && !trimmed.isEmpty()) {
                clauses.add(trimmed);
            }
        }
        return clauses.isEmpty() ? List.of(text) : clauses;
    }

    private List<String> roleAliases(String roleId) {
        return switch (Objects.toString(roleId, "")) {
            case "MERLIN" -> List.of("MERLIN", "梅林");
            case "MORGANA" -> List.of("MORGANA", "莫甘娜");
            case "PERCIVAL" -> List.of("PERCIVAL", "派西维尔");
            case "ASSASSIN" -> List.of("ASSASSIN", "刺客");
            case "LOYAL_SERVANT" -> List.of("LOYAL_SERVANT", "忠臣");
            default -> List.of(roleId);
        };
    }

    private List<String> playerAliases(VisiblePlayerInfo visiblePlayer) {
        List<String> aliases = new ArrayList<>();
        aliases.add(visiblePlayer.playerId());
        if (visiblePlayer.displayName() != null && !Objects.equals(visiblePlayer.displayName(), visiblePlayer.playerId())) {
            aliases.add(visiblePlayer.displayName());
        }
        return List.copyOf(aliases);
    }

    private record CandidateKnowledge(
            String playerId,
            List<String> playerAliases,
            List<String> candidateRoleIds,
            Map<String, List<String>> roleAliasesByRoleId
    ) {
    }
}
