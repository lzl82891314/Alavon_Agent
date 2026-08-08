package com.example.avalon.core.player.memory;

import java.util.List;
import java.util.Map;

public record MemoryUpdate(
        Map<String, Double> suspicionDelta,
        Map<String, Double> trustDelta,
        List<String> observationsToAdd,
        List<String> commitmentsToAdd,
        List<String> inferredFactsToAdd,
        List<Map<String, Object>> worldFactsToAdd,
        List<Map<String, Object>> publicClaimsToAdd,
        Map<String, Double> roleBeliefs,
        Map<String, Object> strategyState,
        Map<String, Object> communicationPlan,
        List<Long> evidenceReferences,
        Long observedThroughSequence,
        String strategyMode,
        String lastSummary
) {
    public MemoryUpdate {
        suspicionDelta = suspicionDelta == null ? Map.of() : Map.copyOf(suspicionDelta);
        trustDelta = trustDelta == null ? Map.of() : Map.copyOf(trustDelta);
        observationsToAdd = observationsToAdd == null ? List.of() : List.copyOf(observationsToAdd);
        commitmentsToAdd = commitmentsToAdd == null ? List.of() : List.copyOf(commitmentsToAdd);
        inferredFactsToAdd = inferredFactsToAdd == null ? List.of() : List.copyOf(inferredFactsToAdd);
        worldFactsToAdd = worldFactsToAdd == null ? List.of() : List.copyOf(worldFactsToAdd);
        publicClaimsToAdd = publicClaimsToAdd == null ? List.of() : List.copyOf(publicClaimsToAdd);
        roleBeliefs = roleBeliefs == null ? Map.of() : Map.copyOf(roleBeliefs);
        strategyState = strategyState == null ? Map.of() : Map.copyOf(strategyState);
        communicationPlan = communicationPlan == null ? Map.of() : Map.copyOf(communicationPlan);
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
        roleBeliefs.forEach((player, probability) -> {
            if (probability == null || probability < 0.0d || probability > 1.0d) {
                throw new IllegalArgumentException("Role belief probability must be between 0 and 1 for " + player);
            }
        });
    }
}

