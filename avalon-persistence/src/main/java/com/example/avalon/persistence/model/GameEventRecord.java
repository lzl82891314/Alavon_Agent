package com.example.avalon.persistence.model;

import java.time.Instant;

public record GameEventRecord(
        String eventId,
        String gameId,
        Long seqNo,
        String type,
        String phase,
        String actorPlayerId,
        String visibility,
        String payloadJson,
        Instant createdAt
) {
    public GameEventRecord {
        payloadJson = payloadJson == null ? "{}" : payloadJson;
    }
}
