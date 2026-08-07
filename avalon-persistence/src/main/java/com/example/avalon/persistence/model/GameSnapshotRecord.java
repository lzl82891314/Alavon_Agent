package com.example.avalon.persistence.model;

import java.time.Instant;

public record GameSnapshotRecord(
        String snapshotId,
        String gameId,
        Long basedOnEventSeqNo,
        Integer roundNo,
        String phase,
        String stateJson,
        Instant createdAt
) {
    public GameSnapshotRecord {
        stateJson = stateJson == null ? "{}" : stateJson;
    }
}

