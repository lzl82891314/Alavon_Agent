package com.example.avalon.testkit;

import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.runtime.model.GameEvent;
import com.example.avalon.runtime.model.RuntimeAuditEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicBehaviorEvaluatorTest {
    @Test
    void scoresProtocolEvidenceAndDeceptionFromFormalArtifacts() {
        List<GameEvent> events = List.of(
                speech(1, "QUESTION", List.of("P2"), List.of()),
                speech(2, "ANSWER", List.of(), List.of(1L)));
        RuntimeAuditEntry audit = new RuntimeAuditEntry(
                "a1", 2L, "P2", "ADMIN_ONLY",
                Map.of(
                        "privateKnowledge", Map.of("camp", "EVIL"),
                        "memory", Map.of(
                                "roleBeliefs", Map.of("P3", 0.4d),
                                "strategyState", Map.of("coverStory", Map.of("persona", "cautious servant")))),
                Map.of("memoryUpdate", Map.of(
                        "roleBeliefs", Map.of("P3", 0.6d),
                        "evidenceReferences", List.of(1L),
                        "beliefEvidenceReferences", Map.of("P3", List.of(1L)),
                        "strategyState", Map.of(
                                "deceptionIntent", "CREATE_ALTERNATIVE_EXPLANATION",
                                "coverStory", Map.of("persona", "cautious servant")))),
                Map.of(), Map.of(), Map.of("valid", true), null, Instant.now());

        StrategicBehaviorReport report = new StrategicBehaviorEvaluator().evaluate(events, List.of(audit));

        assertTrue(report.meetsStructuralGate());
        assertEquals(1.0d, report.challengeTargetRate());
        assertEquals(1.0d, report.targetedResponseReferenceRate());
        assertEquals(1.0d, report.evidenceGroundedBeliefRevisionRate());
        assertEquals(1, report.deceptionPlanCount());
        assertEquals(1.0d, report.narrativeConsistencyRate());
    }

    @Test
    void mechanicalProtocolComplianceDoesNotPassStrategicGate() {
        List<GameEvent> events = List.of(
                speech(1, "QUESTION", List.of("P2"), List.of()),
                speech(2, "ANSWER", List.of(), List.of(1L)));

        StrategicBehaviorReport report = new StrategicBehaviorEvaluator().evaluate(events, List.of());

        assertTrue(report.meetsProtocolGate());
        org.junit.jupiter.api.Assertions.assertFalse(report.meetsStrategicGate());
    }

    private GameEvent speech(long sequence, String speechAct, List<String> mentions, List<Long> replies) {
        return new GameEvent(sequence, "PLAYER_ACTION", GamePhase.DISCUSSION, "P1", Map.of(
                "actionType", "PUBLIC_SPEECH",
                "speech", "strategic statement",
                "speechAct", speechAct,
                "mentions", mentions,
                "replyToEventSequences", replies), Instant.now());
    }
}
