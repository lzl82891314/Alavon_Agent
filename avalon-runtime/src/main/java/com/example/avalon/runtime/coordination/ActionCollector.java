package com.example.avalon.runtime.coordination;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ActionCollector {
    ActionBatch open(NextRequirement requirement);
    SubmissionResult submit(ActionSubmission submission);
    Optional<ActionBatch> findActive(String gameId);
    ActionBatch expire(String batchId, Instant now);
    ActionBatch invalidate(String batchId, String reason);
    void markCommitted(String batchId);

    default String newBatchId(String gameId) {
        return gameId + "-batch-" + UUID.randomUUID();
    }
}
