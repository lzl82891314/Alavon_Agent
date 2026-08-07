package com.example.avalon.core.player.memory;

import java.util.List;
import java.util.Map;

public record MemoryUpdate(
        Map<String, Double> suspicionDelta,
        Map<String, Double> trustDelta,
        List<String> observationsToAdd,
        List<String> commitmentsToAdd,
        List<String> inferredFactsToAdd,
        String strategyMode,
        String lastSummary
) {
    public MemoryUpdate {
        suspicionDelta = suspicionDelta == null ? Map.of() : Map.copyOf(suspicionDelta);
        trustDelta = trustDelta == null ? Map.of() : Map.copyOf(trustDelta);
        observationsToAdd = observationsToAdd == null ? List.of() : List.copyOf(observationsToAdd);
        commitmentsToAdd = commitmentsToAdd == null ? List.of() : List.copyOf(commitmentsToAdd);
        inferredFactsToAdd = inferredFactsToAdd == null ? List.of() : List.copyOf(inferredFactsToAdd);
    }
}

