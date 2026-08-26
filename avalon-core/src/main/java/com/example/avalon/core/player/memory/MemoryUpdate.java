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
        Map<String, List<Long>> beliefEvidenceReferences,
        Long observedThroughSequence,
        String strategyMode,
        String lastSummary,
        Map<String, Map<String, Object>> cognitionSectionStatuses,
        boolean cognitionDegraded,
        List<String> acceptedCognitionSections,
        Map<String, Object> privateActionAssessment
) {

    public MemoryUpdate(
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
            Map<String, List<Long>> beliefEvidenceReferences,
            Long observedThroughSequence,
            String strategyMode,
            String lastSummary
    ) {
        this(suspicionDelta, trustDelta, observationsToAdd, commitmentsToAdd, inferredFactsToAdd,
                worldFactsToAdd, publicClaimsToAdd, roleBeliefs, strategyState, communicationPlan,
                evidenceReferences, beliefEvidenceReferences, observedThroughSequence, strategyMode,
                lastSummary, Map.of(), false, List.of(), Map.of());
    }

    public MemoryUpdate {
        suspicionDelta = suspicionDelta == null ? Map.of() : Map.copyOf(suspicionDelta);
        trustDelta = trustDelta == null ? Map.of() : Map.copyOf(trustDelta);
        observationsToAdd = observationsToAdd == null ? List.of() : List.copyOf(observationsToAdd);
        commitmentsToAdd = commitmentsToAdd == null ? List.of() : List.copyOf(commitmentsToAdd);
        inferredFactsToAdd = inferredFactsToAdd == null ? List.of() : List.copyOf(inferredFactsToAdd);
        worldFactsToAdd = worldFactsToAdd == null ? List.of() : List.copyOf(worldFactsToAdd);
        publicClaimsToAdd = publicClaimsToAdd == null ? List.of() : List.copyOf(publicClaimsToAdd);
        roleBeliefs = roleBeliefs == null ? Map.of() : Map.copyOf(roleBeliefs);
        strategyState = strategyState == null ? Map.of() : new java.util.LinkedHashMap<>(strategyState);
        communicationPlan = communicationPlan == null ? Map.of() : Map.copyOf(communicationPlan);
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
        beliefEvidenceReferences = copyEvidenceBindings(beliefEvidenceReferences);
        cognitionSectionStatuses = copySectionStatuses(cognitionSectionStatuses);
        acceptedCognitionSections = acceptedCognitionSections == null ? List.of() : List.copyOf(acceptedCognitionSections);
        privateActionAssessment = privateActionAssessment == null ? Map.of() : Map.copyOf(privateActionAssessment);
        strategyState = Map.copyOf(strategyState);
        roleBeliefs.forEach((player, probability) -> {
            if (probability == null || probability < 0.0d || probability > 1.0d) {
                throw new IllegalArgumentException("Role belief probability must be between 0 and 1 for " + player);
            }
        });
    }

    private static Map<String, List<Long>> copyEvidenceBindings(Map<String, List<Long>> bindings) {
        if (bindings == null || bindings.isEmpty()) return Map.of();
        java.util.LinkedHashMap<String, List<Long>> copy = new java.util.LinkedHashMap<>();
        bindings.forEach((playerId, references) ->
                copy.put(playerId, references == null ? List.of() : List.copyOf(references)));
        return Map.copyOf(copy);
    }

    private static Map<String, Map<String, Object>> copySectionStatuses(
            Map<String, Map<String, Object>> statuses) {
        if (statuses == null || statuses.isEmpty()) return Map.of();
        java.util.LinkedHashMap<String, Map<String, Object>> copy = new java.util.LinkedHashMap<>();
        statuses.forEach((section, status) ->
                copy.put(section, status == null ? Map.of() : Map.copyOf(status)));
        return Map.copyOf(copy);
    }

}

