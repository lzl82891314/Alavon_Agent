package com.example.avalon.runtime.coordination;

import com.example.avalon.core.game.model.PlayerAction;

import java.time.Instant;

public record ActionSubmission(
        String batchId,
        String playerId,
        PlayerAction action,
        String idempotencyKey,
        long expectedBatchVersion,
        String controllerExecutionId,
        Instant submittedAt
) {
    public ActionSubmission {
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("batchId must not be blank");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId must not be blank");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        submittedAt = submittedAt == null ? Instant.now() : submittedAt;
    }
}
