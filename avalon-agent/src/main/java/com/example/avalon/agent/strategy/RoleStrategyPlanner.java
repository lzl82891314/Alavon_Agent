package com.example.avalon.agent.strategy;

import com.example.avalon.core.game.model.PlayerTurnContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Produces role-specific, non-authoritative guidance for the agent harness. */
@Component
public final class RoleStrategyPlanner {
    public Map<String, Object> plan(PlayerTurnContext context) {
        Map<String, Object> plan = new LinkedHashMap<>();
        String roleId = normalizedRoleId(context.roleId());
        plan.put("roleId", roleId);
        plan.put("camp", context.privateView().camp().name());
        plan.put("phase", context.phase());
        plan.put("objective", objective(roleId, context.privateView().camp().name()));
        plan.put("communicationPolicy", communicationPolicy(roleId));
        plan.put("actionPriorities", actionPriorities(roleId));
        plan.put("existingCommitments", context.memoryState().commitments());
        plan.put("suspicionScores", context.memoryState().suspicionScores());
        plan.put("trustScores", context.memoryState().trustScores());
        return plan;
    }

    private String objective(String roleId, String camp) {
        return switch (roleId) {
            case "MERLIN" -> "Guide good-team decisions using private knowledge while avoiding identity exposure.";
            case "PERCIVAL" -> "Protect plausible Merlin candidates and use public evidence to distinguish them.";
            case "ASSASSIN" -> "Advance evil missions while preserving cover and collecting Merlin candidates.";
            case "MINION", "MORGANA" -> "Coordinate evil outcomes without creating an obvious voting pattern.";
            default -> "Advance the " + camp.toLowerCase() + " camp objective using only visible evidence.";
        };
    }

    private String communicationPolicy(String roleId) {
        return switch (roleId) {
            case "MERLIN" -> "Use calibrated, evidence-based influence; never state hidden role knowledge as public fact.";
            case "ASSASSIN", "MINION", "MORGANA" -> "Maintain a plausible public narrative; do not reveal evil coordination.";
            default -> "Separate observed facts, public claims, and uncertain inferences.";
        };
    }

    private List<String> actionPriorities(String roleId) {
        return switch (roleId) {
            case "MERLIN" -> List.of("avoid leading with hidden knowledge", "protect reliable teams", "preserve ambiguity");
            case "ASSASSIN" -> List.of("preserve cover", "track Merlin candidates", "select legal evil actions");
            case "MINION", "MORGANA" -> List.of("support evil objective", "avoid coordinated-looking votes", "create credible disagreement");
            default -> List.of("use public evidence", "honor prior commitments when justified", "select a legal action");
        };
    }

    private String normalizedRoleId(String roleId) {
        return roleId == null ? "" : roleId.trim().toUpperCase(Locale.ROOT);
    }
}
