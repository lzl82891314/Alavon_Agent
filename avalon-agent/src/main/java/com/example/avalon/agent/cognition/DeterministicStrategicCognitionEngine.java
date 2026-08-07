package com.example.avalon.agent.cognition;

import java.util.LinkedHashMap;
import java.util.List;

/** Baseline harness implementation; an LLM may later replace only the propose step. */
public final class DeterministicStrategicCognitionEngine implements StrategicCognitionEngine {
    @Override
    public PrivateCognitionDraft propose(ObservationBatch observations) {
        long sequence = observations.sourceSequence();
        LinkedHashMap<String, Double> risks = new LinkedHashMap<>();
        observations.events().forEach(event -> {
            Object actor = event.facts().get("actorPlayerId");
            if (actor != null && event.type().contains("REJECTED")) risks.put(String.valueOf(actor), 0.65d);
        });
        BeliefState beliefs = new BeliefState(List.of(), sequence);
        StrategyState strategy = new StrategyState("maximize camp objective while preserving uncertainty", risks, sequence);
        CommunicationPlan communication = new CommunicationPlan(List.of("state observable evidence", "separate fact from suspicion"), "EVIDENCE_REPORT", sequence);
        return new PrivateCognitionDraft(beliefs, strategy, communication, sequence);
    }

    @Override
    public void commit(String gameId, String playerId, PrivateCognitionDraft draft, long acceptedSequence) {
        if (draft == null || draft.sourceSequence() > acceptedSequence) throw new IllegalArgumentException("Cognition draft is stale or from a future batch");
    }
}
