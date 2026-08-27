package com.example.avalon.core.player.memory;

import java.util.LinkedHashMap;
import java.util.Map;

public record EvidenceAssessment(
        long evidenceSequence,
        String evidenceType,
        Map<String, Double> worldLikelihoods,
        String interpretation,
        String uncertainty,
        long assessedAtSequence
) {
    public EvidenceAssessment {
        worldLikelihoods = worldLikelihoods == null ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(worldLikelihoods));
    }
}
