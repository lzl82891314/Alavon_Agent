package com.example.avalon.agent.cognition;

import java.util.List;

public record ObservationBatch(String gameId, String playerId, long sourceSequence,
                               List<ObservedEvent> events) {
    public ObservationBatch {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
