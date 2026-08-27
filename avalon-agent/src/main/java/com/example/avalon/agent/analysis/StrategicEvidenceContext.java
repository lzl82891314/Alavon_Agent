package com.example.avalon.agent.analysis;

import com.example.avalon.core.player.memory.EvidenceAssessment;
import com.example.avalon.core.player.memory.PossibleWorld;
import com.example.avalon.core.player.memory.WorldConstraint;

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
        List<Map<String, Object>> teamCandidates,
        List<WorldConstraint> worldConstraints,
        List<EvidenceAssessment> evidenceAssessments,
        List<PossibleWorld> possibleWorlds
) {
    public StrategicEvidenceContext {
        voteEvidence = immutableMaps(voteEvidence);
        teamHistory = immutableMaps(teamHistory);
        missionConstraints = immutableMaps(missionConstraints);
        contradictionCandidates = immutableMaps(contradictionCandidates);
        teamCandidates = immutableMaps(teamCandidates);
        worldConstraints = worldConstraints == null ? List.of() : List.copyOf(worldConstraints);
        evidenceAssessments = evidenceAssessments == null ? List.of() : List.copyOf(evidenceAssessments);
        possibleWorlds = possibleWorlds == null ? List.of() : List.copyOf(possibleWorlds);
    }

    public Map<String, Object> asMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("observedThroughSequence", observedThroughSequence);
        result.put("voteEvidence", voteEvidence);
        result.put("teamHistory", teamHistory);
        result.put("missionConstraints", missionConstraints);
        result.put("contradictionCandidates", contradictionCandidates);
        result.put("teamCandidates", teamCandidates);
        result.put("worldConstraints", worldConstraints);
        result.put("evidenceAssessments", evidenceAssessments);
        result.put("possibleWorlds", possibleWorlds);
        return Collections.unmodifiableMap(result);
    }

    private static List<Map<String, Object>> immutableMaps(List<Map<String, Object>> values) {
        if (values == null) return List.of();
        return values.stream().map(value -> Collections.unmodifiableMap(new LinkedHashMap<>(value))).toList();
    }
}
