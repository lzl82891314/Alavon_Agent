package com.example.avalon.core.player.memory;

import java.util.List;

public record WorldConstraint(
        String constraintId,
        String kind,
        List<String> subjects,
        List<Long> evidenceReferences,
        String explanation
) {
    public WorldConstraint {
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
    }
}
