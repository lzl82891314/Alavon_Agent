package com.example.avalon.core.game.observation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ObservedGameEvent(
        long sequence,
        String eventType,
        String actorPlayerId,
        FactScope scope,
        Map<String, Object> facts,
        String utterance,
        String speechAct,
        List<String> mentions,
        List<Long> replyToEventSequences,
        Instant occurredAt
) {
    public ObservedGameEvent {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
        replyToEventSequences = replyToEventSequences == null ? List.of() : List.copyOf(replyToEventSequences);
    }
}
