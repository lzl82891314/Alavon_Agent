package com.example.avalon.agent.strategy;

import java.util.List;
import java.util.Locale;

/** Shared role policy used by prompt projection and host validation. */
public final class RoleStrategyPolicy {
    private RoleStrategyPolicy() {
    }

    public static List<String> permittedDeceptionIntents(String role) {
        String normalized = role == null ? "UNKNOWN" : role.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MERLIN" -> List.of("NONE", "WITHHOLD_PRIVATE_KNOWLEDGE", "UNDERSTATE_CONFIDENCE");
            case "PERCIVAL" -> List.of("NONE", "WITHHOLD_PRIVATE_KNOWLEDGE", "BAIT_ASSASSIN");
            case "MORGANA" -> List.of("NONE", "OVERSTATE_SUSPICION", "CREATE_ALTERNATIVE_EXPLANATION", "DISTANCE_FROM_TEAMMATE", "BUILD_FALSE_CREDIBILITY");
            case "ASSASSIN", "MORDRED" -> List.of("NONE", "OVERSTATE_SUSPICION", "CREATE_ALTERNATIVE_EXPLANATION", "DISTANCE_FROM_TEAMMATE");
            case "OBERON" -> List.of("NONE", "OVERSTATE_SUSPICION", "CREATE_ALTERNATIVE_EXPLANATION", "BUILD_FALSE_CREDIBILITY");
            default -> List.of("NONE");
        };
    }
}
