package com.example.avalon.core.game.observation;

import java.util.List;

public record PlayerObservationBatch(
        String gameId,
        String observerPlayerId,
        String agentInstanceId,
        long fromSequenceExclusive,
        long toSequenceInclusive,
        List<ObservedGameEvent> events
) {
    public PlayerObservationBatch {
        events = events == null ? List.of() : List.copyOf(events);
        if (toSequenceInclusive < fromSequenceExclusive) {
            throw new IllegalArgumentException("Observation sequence range is invalid");
        }
    }
}
