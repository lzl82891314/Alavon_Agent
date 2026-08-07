package com.example.avalon.runtime.coordination;

import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.model.PlayerAction;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ActionBatch {
    private final String batchId;
    private final String gameId;
    private final long sourceGameVersion;
    private final String turnToken;
    private final GamePhase phase;
    private final String actionType;
    private final Set<String> requiredPlayers;
    private final Map<String, ActionSubmission> submissions = new LinkedHashMap<>();
    private Instant createdAt;
    private final Instant deadline;
    private ActionBatchStatus status;
    private long batchVersion;

    public ActionBatch(String batchId,
                       String gameId,
                       long sourceGameVersion,
                       String turnToken,
                       GamePhase phase,
                       String actionType,
                       Set<String> requiredPlayers,
                       Instant deadline) {
        this.batchId = requireText(batchId, "batchId");
        this.gameId = requireText(gameId, "gameId");
        this.turnToken = requireText(turnToken, "turnToken");
        this.phase = phase;
        this.actionType = requireText(actionType, "actionType");
        this.requiredPlayers = Collections.unmodifiableSet(new LinkedHashSet<>(requiredPlayers));
        if (this.requiredPlayers.isEmpty()) {
            throw new IllegalArgumentException("requiredPlayers must not be empty");
        }
        this.sourceGameVersion = sourceGameVersion;
        this.createdAt = Instant.now();
        this.deadline = deadline == null ? this.createdAt.plusSeconds(120) : deadline;
        this.status = ActionBatchStatus.OPEN;
    }

    public static ActionBatch restore(String batchId, String gameId, long sourceGameVersion, String turnToken,
                                      GamePhase phase, String actionType, Set<String> requiredPlayers,
                                      Instant createdAt, Instant deadline, ActionBatchStatus status, long batchVersion,
                                      Map<String, ActionSubmission> submissions) {
        ActionBatch batch = new ActionBatch(batchId, gameId, sourceGameVersion, turnToken, phase, actionType, requiredPlayers, deadline);
        batch.submissions.clear();
        batch.submissions.putAll(submissions == null ? Map.of() : submissions);
        batch.createdAt = createdAt == null ? batch.createdAt : createdAt;
        batch.status = status;
        batch.batchVersion = batchVersion;
        return batch;
    }

    public synchronized void submit(ActionSubmission submission) {
        ensureWritable();
        if (!batchId.equals(submission.batchId())) {
            throw new IllegalArgumentException("Submission belongs to another batch");
        }
        if (!requiredPlayers.contains(submission.playerId())) {
            throw new IllegalArgumentException("Player is not required by this batch: " + submission.playerId());
        }
        if (submissions.containsKey(submission.playerId())) {
            ActionSubmission existing = submissions.get(submission.playerId());
            if (!existing.idempotencyKey().equals(submission.idempotencyKey())) {
                throw new IllegalStateException("Player already submitted an action");
            }
            if (!existing.action().equals(submission.action())) {
                throw new IllegalStateException("Idempotency key was reused with a different action");
            }
            return;
        }
        if (submission.expectedBatchVersion() != batchVersion) {
            throw new IllegalStateException("Stale action batch version");
        }
        submissions.put(submission.playerId(), submission);
        batchVersion++;
        if (submissions.size() == requiredPlayers.size()) {
            status = ActionBatchStatus.COMPLETED;
        } else {
            status = ActionBatchStatus.PARTIALLY_COLLECTED;
        }
    }

    public synchronized void markCommitted() {
        if (status != ActionBatchStatus.COMPLETED) {
            throw new IllegalStateException("Only completed batches can be committed");
        }
        status = ActionBatchStatus.COMMITTED;
        batchVersion++;
    }

    public synchronized void invalidate(String reason) {
        if (status == ActionBatchStatus.COMMITTED) {
            throw new IllegalStateException("Committed batch cannot be invalidated");
        }
        status = ActionBatchStatus.INVALIDATED;
        batchVersion++;
    }

    public synchronized void expire(Instant now) {
        if (!isComplete() && (now == null || !now.isBefore(deadline))) {
            status = ActionBatchStatus.EXPIRED;
            batchVersion++;
        }
    }

    public String batchId() { return batchId; }
    public String gameId() { return gameId; }
    public long sourceGameVersion() { return sourceGameVersion; }
    public String turnToken() { return turnToken; }
    public GamePhase phase() { return phase; }
    public String actionType() { return actionType; }
    public Set<String> requiredPlayers() { return requiredPlayers; }
    public synchronized Map<String, ActionSubmission> submissions() { return Collections.unmodifiableMap(new LinkedHashMap<>(submissions)); }
    public Instant createdAt() { return createdAt; }
    public Instant deadline() { return deadline; }
    public synchronized ActionBatchStatus status() { return status; }
    public synchronized long batchVersion() { return batchVersion; }
    public synchronized boolean isComplete() { return status == ActionBatchStatus.COMPLETED || status == ActionBatchStatus.COMMITTED; }
    public synchronized Set<String> missingPlayers() {
        Set<String> missing = new LinkedHashSet<>(requiredPlayers);
        missing.removeAll(submissions.keySet());
        return missing;
    }
    public synchronized Map<String, PlayerAction> completedActions() {
        Map<String, PlayerAction> actions = new LinkedHashMap<>();
        submissions.forEach((playerId, submission) -> actions.put(playerId, submission.action()));
        return Collections.unmodifiableMap(actions);
    }

    private void ensureWritable() {
        if (status != ActionBatchStatus.OPEN && status != ActionBatchStatus.PARTIALLY_COLLECTED) {
            throw new IllegalStateException("Batch is not accepting submissions: " + status);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
