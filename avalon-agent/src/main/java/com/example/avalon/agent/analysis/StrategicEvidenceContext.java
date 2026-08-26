package com.example.avalon.agent.analysis;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

/** Immutable, public-evidence-only context for strategic reasoning. */
public record StrategicEvidenceContext(
        long observedThroughSequence,
        List<Map<String, Object>> voteEvidence,
        List<Map<String, Object>> teamHistory,
        List<Map<String, Object>> missionConstraints,
        List<Map<String, Object>> contradictionCandidates,
        List<Map<String, Object>> teamCandidates
) {
    public StrategicEvidenceContext {
        voteEvidence = immutableMaps(voteEvidence);
        teamHistory = immutableMaps(teamHistory);
        missionConstraints = immutableMaps(missionConstraints);
        contradictionCandidates = immutableMaps(contradictionCandidates);
        teamCandidates = immutableMaps(teamCandidates);
    }

    public Map<String, Object> asMap() {
        return Map.of(
                "observedThroughSequence", observedThroughSequence,
                "voteEvidence", voteEvidence,
                "teamHistory", teamHistory,
                "missionConstraints", missionConstraints,
                "contradictionCandidates", contradictionCandidates,
                "teamCandidates", teamCandidates);
    }

    private static List<Map<String, Object>> immutableMaps(List<Map<String, Object>> values) {
        if (values == null) return List.of();
        return values.stream().map(value -> Collections.unmodifiableMap(new LinkedHashMap<>(value))).toList();
    }
}
