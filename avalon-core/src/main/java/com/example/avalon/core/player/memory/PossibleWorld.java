package com.example.avalon.core.player.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PossibleWorld(
        String worldId,
        Map<String, String> roleAssignments,
        List<WorldConstraint> constraints,
        double priorWeight,
        double posteriorWeight,
        List<Long> supportingEvidenceReferences,
        List<Long> opposingEvidenceReferences,
        List<BehaviorPrediction> predictions,
        long updatedAtSequence
) {
    public PossibleWorld {
        roleAssignments = roleAssignments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(roleAssignments));
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        supportingEvidenceReferences = supportingEvidenceReferences == null
                ? List.of() : List.copyOf(supportingEvidenceReferences);
        opposingEvidenceReferences = opposingEvidenceReferences == null
                ? List.of() : List.copyOf(opposingEvidenceReferences);
        predictions = predictions == null ? List.of() : List.copyOf(predictions);
    }
}
