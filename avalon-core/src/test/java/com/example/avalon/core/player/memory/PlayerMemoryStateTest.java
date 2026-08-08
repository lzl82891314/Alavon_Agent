package com.example.avalon.core.player.memory;

import com.example.avalon.core.game.enums.Camp;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerMemoryStateTest {
    @Test
    void commitsStructuredCognitionWithoutCollapsingClaimsIntoFacts() {
        PlayerMemoryState memory = PlayerMemoryState.empty("g1", "P1", "LOYAL_SERVANT", Camp.GOOD, Instant.now());
        MemoryUpdate update = new MemoryUpdate(Map.of(), Map.of(), List.of(), List.of("reject P2 without explanation"),
                List.of(), List.of(Map.of("sequence", 2L, "eventType", "TEAM_PROPOSED")),
                List.of(Map.of("sequence", 3L, "utterance", "P2 claims innocence")), Map.of("P2", 0.62d),
                Map.of("mode", "INFORMATION_SEEKING"), Map.of("speechAct", "QUESTION"), List.of(2L, 3L),
                Map.of("P2", List.of(2L, 3L)),
                3L, "INFORMATION_SEEKING", "Need P2 to explain the vote.");

        PlayerMemoryState committed = memory.merge(update, Instant.now());

        assertEquals(1, committed.worldFacts().size());
        assertEquals(1, committed.publicClaims().size());
        assertEquals(0.62d, committed.roleBeliefs().get("P2"));
        assertEquals(List.of(2L, 3L), committed.beliefEvidenceReferences().get("P2"));
        assertEquals(3L, committed.lastObservedSequence());
    }
}
