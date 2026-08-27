package com.example.avalon.core.player.memory;

import java.util.List;

public record BehaviorPrediction(
        String predictionId,
        String worldId,
        String subjectPlayerId,
        String situation,
        List<String> expectedBehaviors,
        List<Long> discriminatingObservationReferences,
        String status,
        long validThroughSequence
) {
    public BehaviorPrediction {
        expectedBehaviors = expectedBehaviors == null ? List.of() : List.copyOf(expectedBehaviors);
        discriminatingObservationReferences = discriminatingObservationReferences == null
                ? List.of() : List.copyOf(discriminatingObservationReferences);
    }
}
