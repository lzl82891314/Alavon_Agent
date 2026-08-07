package com.example.avalon.agent.cognition;

import java.time.Instant;
import java.util.Map;

public record ObservedEvent(long sequence, String type, CognitionScope scope, String actorId,
                            Map<String, Object> facts, Instant observedAt) {
    public ObservedEvent {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
        observedAt = observedAt == null ? Instant.now() : observedAt;
    }
}
