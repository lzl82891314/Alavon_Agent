package com.example.avalon.persistence.model;

import java.time.Instant;

public record PlayerMemorySnapshotRecord(
        String snapshotId,
        String gameId,
        String playerId,
        Long basedOnEventSeqNo,
        String memoryJson,
        Instant createdAt
) {
    public PlayerMemorySnapshotRecord {
        memoryJson = memoryJson == null ? "{}" : memoryJson;
    }
}

