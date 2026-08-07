package com.example.avalon.agent.cognition;

public interface StrategicCognitionEngine {
    PrivateCognitionDraft propose(ObservationBatch observations);
    void commit(String gameId, String playerId, PrivateCognitionDraft draft, long acceptedSequence);
}
