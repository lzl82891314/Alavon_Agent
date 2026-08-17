package com.example.avalon.core.player.memory;

import com.example.avalon.core.game.enums.Camp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PlayerMemoryState(
        String gameId,
        String playerId,
        Long version,
        String roleId,
        Camp camp,
        Map<String, Double> suspicionScores,
        Map<String, Double> trustScores,
        List<String> observations,
        List<String> commitments,
        List<String> inferredFacts,
        List<Map<String, Object>> worldFacts,
        List<Map<String, Object>> publicClaims,
        Map<String, Double> roleBeliefs,
        Map<String, Object> strategyState,
        Map<String, Object> communicationPlan,
        Map<String, List<Long>> beliefEvidenceReferences,
        Long lastObservedSequence,
        String agentInstanceId,
        String strategyMode,
        String lastSummary,
        Instant updatedAt
) {
    public PlayerMemoryState {
        suspicionScores = suspicionScores == null ? Map.of() : Map.copyOf(suspicionScores);
        trustScores = trustScores == null ? Map.of() : Map.copyOf(trustScores);
        observations = observations == null ? List.of() : List.copyOf(observations);
        commitments = commitments == null ? List.of() : List.copyOf(commitments);
        inferredFacts = inferredFacts == null ? List.of() : List.copyOf(inferredFacts);
        worldFacts = worldFacts == null ? List.of() : List.copyOf(worldFacts);
        publicClaims = publicClaims == null ? List.of() : List.copyOf(publicClaims);
        roleBeliefs = roleBeliefs == null ? Map.of() : Map.copyOf(roleBeliefs);
        strategyState = strategyState == null ? Map.of() : Map.copyOf(strategyState);
        communicationPlan = communicationPlan == null ? Map.of() : Map.copyOf(communicationPlan);
        beliefEvidenceReferences = copyEvidenceBindings(beliefEvidenceReferences);
    }

    public static PlayerMemoryState empty(String gameId, String playerId, String roleId, Camp camp, Instant now) {
        return new PlayerMemoryState(
                gameId,
                playerId,
                0L,
                roleId,
                camp,
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                0L,
                playerId + ":primary",
                "NEUTRAL",
                null,
                now
        );
    }

    public PlayerMemoryState merge(MemoryUpdate update, Instant now) {
        Map<String, Double> nextSuspicion = new LinkedHashMap<>(suspicionScores);
        update.suspicionDelta().forEach((key, value) -> nextSuspicion.merge(key, value, Double::sum));

        Map<String, Double> nextTrust = new LinkedHashMap<>(trustScores);
        update.trustDelta().forEach((key, value) -> nextTrust.merge(key, value, Double::sum));

        List<String> nextObservations = appendStringsBounded(observations, update.observationsToAdd(), 80);
        List<String> nextCommitments = mergeCommitments(commitments, update.commitmentsToAdd(), 40);
        List<String> nextFacts = appendStringsBounded(inferredFacts, update.inferredFactsToAdd(), 80);

        List<Map<String, Object>> nextWorldFacts = appendBounded(worldFacts, update.worldFactsToAdd(), 120);
        List<Map<String, Object>> nextPublicClaims = appendBounded(publicClaims, update.publicClaimsToAdd(), 120);

        return new PlayerMemoryState(
                gameId,
                playerId,
                version + 1,
                roleId,
                camp,
                nextSuspicion,
                nextTrust,
                nextObservations,
                nextCommitments,
                nextFacts,
                nextWorldFacts,
                nextPublicClaims,
                update.roleBeliefs().isEmpty() ? roleBeliefs : update.roleBeliefs(),
                update.strategyState().isEmpty() ? strategyState : update.strategyState(),
                update.communicationPlan().isEmpty() ? communicationPlan : update.communicationPlan(),
                mergeEvidenceBindings(beliefEvidenceReferences, update.beliefEvidenceReferences()),
                update.observedThroughSequence() == null ? lastObservedSequence : update.observedThroughSequence(),
                agentInstanceId,
                update.strategyMode() == null ? strategyMode : update.strategyMode(),
                update.lastSummary() == null ? lastSummary : truncate(update.lastSummary(), 2000),
                now
        );
    }

    private static Map<String, List<Long>> copyEvidenceBindings(Map<String, List<Long>> bindings) {
        if (bindings == null || bindings.isEmpty()) return Map.of();
        Map<String, List<Long>> copy = new LinkedHashMap<>();
        bindings.forEach((playerId, references) ->
                copy.put(playerId, references == null ? List.of() : List.copyOf(references)));
        return Map.copyOf(copy);
    }

    private static Map<String, List<Long>> mergeEvidenceBindings(Map<String, List<Long>> current,
                                                                  Map<String, List<Long>> additions) {
        if (additions == null || additions.isEmpty()) return current;
        Map<String, List<Long>> merged = new LinkedHashMap<>(current);
        additions.forEach((playerId, references) -> {
            List<Long> values = new ArrayList<>(merged.getOrDefault(playerId, List.of()));
            if (references != null) values.addAll(references);
            merged.put(playerId, List.copyOf(values.subList(Math.max(0, values.size() - 20), values.size())));
        });
        return copyEvidenceBindings(merged);
    }

    private static List<Map<String, Object>> appendBounded(List<Map<String, Object>> current,
                                                            List<Map<String, Object>> additions,
                                                            int limit) {
        java.util.Set<Long> knownSequences = new java.util.HashSet<>();
        List<Map<String, Object>> combined = new ArrayList<>();
        current.forEach(value -> {
            java.util.Optional<Long> sequence = sequenceOf(value);
            if (sequence.isEmpty() || knownSequences.add(sequence.get())) {
                combined.add(value);
            }
        });
        additions.forEach(value -> {
            java.util.Optional<Long> sequence = sequenceOf(value);
            if (sequence.isEmpty() || knownSequences.add(sequence.get())) {
                combined.add(value);
            }
        });
        return List.copyOf(combined.subList(Math.max(0, combined.size() - limit), combined.size()));
    }

    private static java.util.Optional<Long> sequenceOf(Map<String, Object> value) {
        if (value == null) return java.util.Optional.empty();
        Object sequence = value.get("sourceEventSequence");
        if (!(sequence instanceof Number)) sequence = value.get("sequence");
        return sequence instanceof Number number
                ? java.util.Optional.of(number.longValue())
                : java.util.Optional.empty();
    }

    private static List<String> appendStringsBounded(List<String> current, List<String> additions, int limit) {
        List<String> combined = new ArrayList<>(current);
        additions.stream().map(value -> truncate(value, 500)).forEach(combined::add);
        return List.copyOf(combined.subList(Math.max(0, combined.size() - limit), combined.size()));
    }

    private static List<String> mergeCommitments(List<String> current, List<String> additions, int limit) {
        List<String> merged = new ArrayList<>(current);
        for (String addition : additions) {
            java.util.Optional<Long> sequence = commitmentSequence(addition);
            if (sequence.isPresent()) {
                boolean replaced = false;
                for (int index = 0; index < merged.size(); index++) {
                    if (commitmentSequence(merged.get(index)).equals(sequence)) {
                        merged.set(index, addition);
                        replaced = true;
                        break;
                    }
                }
                if (replaced) continue;
            }
            merged.add(truncate(addition, 500));
        }
        return List.copyOf(merged.subList(Math.max(0, merged.size() - limit), merged.size()));
    }

    private static java.util.Optional<Long> commitmentSequence(String value) {
        if (value == null) return java.util.Optional.empty();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\\"sourceEventSequence\\\"\\s*:\\s*(\\d+)")
                .matcher(value);
        return matcher.find() ? java.util.Optional.of(Long.parseLong(matcher.group(1))) : java.util.Optional.empty();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}

