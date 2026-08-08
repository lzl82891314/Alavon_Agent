package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {
    @Test
    void promptOptimizesForEvidenceAndStrategyInsteadOfMinimalOutput() {
        AgentTurnRequest request = new AgentTurnRequest();
        request.setGameId("g1");
        request.setPlayerId("P1");
        request.setRoleId("MORGANA");
        request.setPhase("DISCUSSION");
        request.setAllowedActions(List.of("PUBLIC_SPEECH"));
        request.setObservationFromSequence(4);
        request.setObservationToSequence(7);
        request.setObservationDelta(List.of(Map.of("sequence", 7L, "scope", "PUBLIC_CLAIM")));
        request.setDiscussionDirective(Map.of("stage", "CHALLENGE_WINDOW", "allowedSpeechActs", List.of("QUESTION")));

        String prompt = new PromptBuilder().build(request);

        assertTrue(prompt.contains("PUBLIC_CLAIM 只是某人的公开主张"));
        assertTrue(prompt.contains("desiredAudienceBeliefs"));
        assertTrue(prompt.contains("deceptionIntent"));
        assertFalse(prompt.contains("优先返回最小合法 JSON"));
        assertFalse(prompt.contains("我先给出公开看法"));
    }
}
