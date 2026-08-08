package com.example.avalon.agent.service;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryContextProjectorTest {
    @Test
    void keepsRecentStrategicStateWithinInputBudget() throws Exception {
        List<Map<String, Object>> facts = entries("FACT", 100);
        List<Map<String, Object>> claims = entries("CLAIM", 100);
        PlayerMemoryState memory = new PlayerMemoryState(
                "g1", "P1", 8L, "LOYAL_SERVANT", Camp.GOOD,
                Map.of(), Map.of(), strings("observation", 80), strings("commitment", 40), strings("inference", 80),
                facts, claims, Map.of("P2", 0.65d),
                Map.of("mode", "TEST", "objective", "x".repeat(2_000)),
                Map.of("publicMessage", "y".repeat(2_000)),
                Map.of("P2", List.of(199L)),
                200L, "P1:primary", "TEST", "latest summary", Instant.now());

        Map<String, Object> projected = new MemoryContextProjector(2_500).project(memory);
        int serializedLength = new ObjectMapper().writeValueAsString(projected).length();

        assertTrue(serializedLength <= 2_500,
                "projected memory must honor its character budget, actual=" + serializedLength);
        assertEquals(200L, projected.get("lastObservedSequence"));
        assertEquals(Map.of("P2", 0.65d), projected.get("roleBeliefs"));
        assertTrue((Boolean) ((Map<?, ?>) projected.get("contextWindow")).get("compacted"));
    }

    private List<Map<String, Object>> entries(String prefix, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> Map.<String, Object>of(
                        "sequence", index,
                        "eventType", prefix,
                        "facts", "z".repeat(120)))
                .toList();
    }

    private List<String> strings(String prefix, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(index -> prefix + index + "-" + "z".repeat(80)).toList();
    }
}
