package com.example.avalon.core.player.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StrategicActionCandidate(
        String candidateId,
        String actionType,
        Map<String, Object> action,
        Map<String, Double> worldOutcomes,
        double expectedCampValue,
        double expectedInformationGain,
        double exposureCost,
        double commitmentCost,
        double executionRisk,
        List<Long> evidenceReferences,
        List<Long> followUpObservationReferences
) {
    public StrategicActionCandidate {
        action = action == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(action));
        worldOutcomes = worldOutcomes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(worldOutcomes));
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
        followUpObservationReferences = followUpObservationReferences == null
                ? List.of() : List.copyOf(followUpObservationReferences);
    }
}
