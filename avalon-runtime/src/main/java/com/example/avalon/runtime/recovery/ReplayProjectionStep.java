package com.example.avalon.runtime.recovery;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record ReplayProjectionStep(
        Long seqNo,
        String eventType,
        String phase,
        String actorId,
        String replayKind,
        String summary,
        Map<String, Object> payload,
        Instant createdAt) {
    public ReplayProjectionStep {
        payload = payload == null ? Map.of() : new LinkedHashMap<>(payload);
    }
}
