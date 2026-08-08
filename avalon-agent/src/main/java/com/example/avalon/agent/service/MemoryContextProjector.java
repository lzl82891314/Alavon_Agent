package com.example.avalon.agent.service;

import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Produces a bounded, recent-first memory view for model input. */
public final class MemoryContextProjector {
    static final int DEFAULT_MAX_CHARS = 12_000;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final int maxChars;

    public MemoryContextProjector() {
        this(DEFAULT_MAX_CHARS);
    }

    MemoryContextProjector(int maxChars) {
        if (maxChars < 2_000) throw new IllegalArgumentException("Memory context budget is too small");
        this.maxChars = maxChars;
    }

    public Map<String, Object> project(PlayerMemoryState memory) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", memory.version());
        result.put("roleBeliefs", memory.roleBeliefs());
        result.put("beliefEvidenceReferences", memory.beliefEvidenceReferences());
        result.put("strategyState", compact(memory.strategyState(), 0));
        result.put("communicationPlan", compact(memory.communicationPlan(), 0));
        result.put("commitments", tail(memory.commitments(), 20));
        result.put("observations", tail(memory.observations(), 20));
        result.put("inferredFacts", tail(memory.inferredFacts(), 20));
        result.put("worldFacts", new ArrayList<>(tail(memory.worldFacts(), 40).stream()
                .map(item -> compact(item, 0)).toList()));
        result.put("publicClaims", new ArrayList<>(tail(memory.publicClaims(), 40).stream()
                .map(item -> compact(item, 0)).toList()));
        result.put("strategyMode", memory.strategyMode());
        result.put("lastSummary", truncate(memory.lastSummary(), 1_500));
        result.put("lastObservedSequence", memory.lastObservedSequence());
        result.put("agentInstanceId", memory.agentInstanceId());

        int originalFacts = memory.worldFacts().size();
        int originalClaims = memory.publicClaims().size();
        int contentBudget = Math.max(1_500, maxChars - 600);
        trimToBudget(result, contentBudget);
        if (serializedLength(result) > contentBudget) {
            result.put("strategyState", Map.of("compactedSummary", truncate(String.valueOf(result.get("strategyState")), 600)));
            result.put("communicationPlan", Map.of("compactedSummary", truncate(String.valueOf(result.get("communicationPlan")), 400)));
            trimToBudget(result, contentBudget);
        }
        int includedFacts = ((List<?>) result.get("worldFacts")).size();
        int includedClaims = ((List<?>) result.get("publicClaims")).size();
        result.put("contextWindow", Map.of(
                "maxChars", maxChars,
                "estimatedTokens", Math.max(1, serializedLength(result) / 4),
                "worldFactsIncluded", includedFacts,
                "publicClaimsIncluded", includedClaims,
                "compacted", includedFacts < originalFacts || includedClaims < originalClaims));
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    @SuppressWarnings("unchecked")
    private void trimToBudget(Map<String, Object> result, int budget) {
        List<Object> facts = (List<Object>) result.get("worldFacts");
        List<Object> claims = (List<Object>) result.get("publicClaims");
        List<Object> commitments = mutable(result, "commitments");
        List<Object> observations = mutable(result, "observations");
        List<Object> inferred = mutable(result, "inferredFacts");
        while (serializedLength(result) > budget
                && (!facts.isEmpty() || !claims.isEmpty() || !commitments.isEmpty()
                || !observations.isEmpty() || !inferred.isEmpty())) {
            List<Object> target = largest(facts, claims, commitments, observations, inferred);
            target.remove(0);
        }
    }

    private List<Object> mutable(Map<String, Object> result, String key) {
        List<Object> values = new ArrayList<>((List<?>) result.get(key));
        result.put(key, values);
        return values;
    }

    @SafeVarargs
    private final List<Object> largest(List<Object>... lists) {
        List<Object> largest = lists[0];
        for (List<Object> list : lists) if (list.size() > largest.size()) largest = list;
        return largest;
    }

    private Object compact(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof String text) return truncate(text, 1_000);
        if (depth >= 4) return String.valueOf(value);
        if (value instanceof List<?> list) {
            return tail(list, 20).stream().map(item -> compact(item, depth + 1)).toList();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> compacted = new LinkedHashMap<>();
            map.entrySet().stream().limit(32)
                    .forEach(entry -> compacted.put(String.valueOf(entry.getKey()), compact(entry.getValue(), depth + 1)));
            return compacted;
        }
        return truncate(String.valueOf(value), 1_000);
    }

    private <T> List<T> tail(List<T> values, int limit) {
        return List.copyOf(values.subList(Math.max(0, values.size() - limit), values.size()));
    }

    private int serializedLength(Object value) {
        try {
            return json.writeValueAsString(value).length();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot estimate memory context size", exception);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
