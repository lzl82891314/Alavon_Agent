package com.example.avalon.runtime.coordination;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryActionCollector implements ActionCollector {
    private final Map<String, ActionBatch> batchesById = new ConcurrentHashMap<>();

    @Override
    public synchronized ActionBatch open(NextRequirement requirement) {
        findActive(requirement.gameId()).ifPresent(active -> {
            throw new IllegalStateException("Game already has an active action batch: " + active.batchId());
        });
        ActionBatch batch;
        if (requirement instanceof SinglePlayerActionRequirement single) {
            batch = new ActionBatch(newBatchId(requirement.gameId()), requirement.gameId(), requirement.sourceGameVersion(),
                    requirement.gameId() + "-turn-" + requirement.sourceGameVersion(), requirement.phase(), single.actionType(),
                    java.util.Set.of(single.playerId()), single.deadline());
        } else if (requirement instanceof ParallelPlayerActionRequirement parallel) {
            batch = new ActionBatch(newBatchId(requirement.gameId()), requirement.gameId(), requirement.sourceGameVersion(),
                    requirement.gameId() + "-turn-" + requirement.sourceGameVersion(), requirement.phase(), parallel.actionType(),
                    parallel.requiredPlayers(), parallel.deadline());
        } else if (requirement instanceof ExternalPlayerActionRequirement external) {
            batch = new ActionBatch(newBatchId(requirement.gameId()), requirement.gameId(), requirement.sourceGameVersion(),
                    requirement.gameId() + "-turn-" + requirement.sourceGameVersion(), requirement.phase(), external.actionType(),
                    new java.util.LinkedHashSet<>(external.requiredPlayers()), external.deadline());
        } else {
            throw new IllegalArgumentException("Only player action requirements can open a batch");
        }
        batchesById.put(batch.batchId(), batch);
        return batch;
    }

    @Override
    public SubmissionResult submit(ActionSubmission submission) {
        ActionBatch batch = require(submission.batchId());
        boolean replay = batch.submissions().containsKey(submission.playerId())
                && batch.submissions().get(submission.playerId()).idempotencyKey().equals(submission.idempotencyKey());
        batch.submit(submission);
        return new SubmissionResult(batch, true, replay, replay ? "idempotent replay" : "accepted");
    }

    @Override
    public Optional<ActionBatch> findActive(String gameId) {
        return batchesById.values().stream()
                .filter(batch -> batch.gameId().equals(gameId))
                .filter(batch -> batch.status() == ActionBatchStatus.OPEN
                        || batch.status() == ActionBatchStatus.PARTIALLY_COLLECTED
                        || batch.status() == ActionBatchStatus.COMPLETED)
                .findFirst();
    }

    @Override
    public ActionBatch expire(String batchId, Instant now) {
        ActionBatch batch = require(batchId);
        batch.expire(now);
        return batch;
    }

    @Override
    public ActionBatch invalidate(String batchId, String reason) {
        ActionBatch batch = require(batchId);
        batch.invalidate(reason);
        return batch;
    }

    @Override
    public void markCommitted(String batchId) {
        require(batchId).markCommitted();
    }

    private ActionBatch require(String batchId) {
        ActionBatch batch = batchesById.get(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("Unknown action batch: " + batchId);
        }
        return batch;
    }
}
