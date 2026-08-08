package com.example.avalon.runtime.persistence;

import com.example.avalon.persistence.model.GameEventRecord;
import com.example.avalon.persistence.model.GameSnapshotRecord;
import com.example.avalon.persistence.model.PlayerMemorySnapshotRecord;
import com.example.avalon.persistence.model.AuditRecord;
import com.example.avalon.persistence.store.AuditRecordStore;
import com.example.avalon.persistence.store.GameEventStore;
import com.example.avalon.persistence.store.GameSnapshotStore;
import com.example.avalon.persistence.store.PlayerMemorySnapshotStore;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.runtime.model.GameEvent;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.RuntimeAuditEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.HexFormat;

public class RuntimePersistenceService {
    private final GameEventStore gameEventStore;
    private final GameSnapshotStore gameSnapshotStore;
    private final PlayerMemorySnapshotStore playerMemorySnapshotStore;
    private final AuditRecordStore auditRecordStore;
    private final RuntimeStateCodec runtimeStateCodec;
    private final ObjectMapper objectMapper;
    private final int snapshotEveryNEvents;

    public RuntimePersistenceService(
            GameEventStore gameEventStore,
            GameSnapshotStore gameSnapshotStore,
            PlayerMemorySnapshotStore playerMemorySnapshotStore,
            RuntimeStateCodec runtimeStateCodec
    ) {
        this(gameEventStore, gameSnapshotStore, playerMemorySnapshotStore, null, runtimeStateCodec, 10);
    }

    public RuntimePersistenceService(
            GameEventStore gameEventStore,
            GameSnapshotStore gameSnapshotStore,
            PlayerMemorySnapshotStore playerMemorySnapshotStore,
            AuditRecordStore auditRecordStore,
            RuntimeStateCodec runtimeStateCodec
    ) {
        this(gameEventStore, gameSnapshotStore, playerMemorySnapshotStore, auditRecordStore, runtimeStateCodec, 10);
    }

    public RuntimePersistenceService(
            GameEventStore gameEventStore,
            GameSnapshotStore gameSnapshotStore,
            PlayerMemorySnapshotStore playerMemorySnapshotStore,
            RuntimeStateCodec runtimeStateCodec,
            int snapshotEveryNEvents
    ) {
        this(gameEventStore, gameSnapshotStore, playerMemorySnapshotStore, null, runtimeStateCodec, snapshotEveryNEvents);
    }

    public RuntimePersistenceService(
            GameEventStore gameEventStore,
            GameSnapshotStore gameSnapshotStore,
            PlayerMemorySnapshotStore playerMemorySnapshotStore,
            AuditRecordStore auditRecordStore,
            RuntimeStateCodec runtimeStateCodec,
            int snapshotEveryNEvents
    ) {
        this.gameEventStore = gameEventStore;
        this.gameSnapshotStore = gameSnapshotStore;
        this.playerMemorySnapshotStore = playerMemorySnapshotStore;
        this.auditRecordStore = auditRecordStore;
        this.runtimeStateCodec = runtimeStateCodec;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.snapshotEveryNEvents = snapshotEveryNEvents;
    }

    public void persist(GameRuntimeState state) {
        long persistedCount = gameEventStore.countByGameId(state.generatedGameId());
        for (GameEvent event : state.events()) {
            if (event.seqNo() > persistedCount) {
                gameEventStore.save(toRecord(state.generatedGameId(), event));
            }
        }
        persistAudits(state);
        long latestSeqNo = state.events().isEmpty() ? 0L : state.events().get(state.events().size() - 1).seqNo();
        var latestSnapshot = gameSnapshotStore.findLatestByGameId(state.generatedGameId());
        if (shouldSnapshot(latestSeqNo, latestSnapshot.map(GameSnapshotRecord::basedOnEventSeqNo).orElse(null), state.status())) {
            saveSnapshot(state, latestSeqNo);
            saveMemorySnapshots(state, latestSeqNo);
        }
    }

    public void saveSnapshot(GameRuntimeState state, long basedOnEventSeqNo) {
        gameSnapshotStore.save(new GameSnapshotRecord(
                snapshotId(state.generatedGameId(), basedOnEventSeqNo),
                state.generatedGameId(),
                basedOnEventSeqNo,
                state.roundNo(),
                state.phase().name(),
                runtimeStateCodec.serialize(state),
                Instant.now()));
    }

    public void saveMemorySnapshots(GameRuntimeState state, long basedOnEventSeqNo) {
        for (Map.Entry<String, Map<String, Object>> entry : state.memoryByPlayerId().entrySet()) {
            playerMemorySnapshotStore.save(new PlayerMemorySnapshotRecord(
                    UUID.randomUUID().toString(),
                    state.generatedGameId(),
                    entry.getKey(),
                    basedOnEventSeqNo,
                    writeJson(entry.getValue()),
                    Instant.now()));
        }
    }

    private boolean shouldSnapshot(long latestSeqNo, Long latestSnapshotSeqNo, GameStatus status) {
        if (latestSeqNo <= 0) {
            return false;
        }
        if (latestSnapshotSeqNo == null) {
            return true;
        }
        if (latestSnapshotSeqNo >= latestSeqNo) {
            return false;
        }
        if (status == GameStatus.ENDED) {
            return true;
        }
        return latestSeqNo % snapshotEveryNEvents == 0;
    }

    private GameEventRecord toRecord(String gameId, GameEvent event) {
        return new GameEventRecord(
                gameId + "-" + event.seqNo(),
                gameId,
                event.seqNo(),
                event.type(),
                event.phase().name(),
                event.actorId(),
                eventVisibility(event),
                writeJson(event.payload()),
                event.createdAt()
        );
    }

    private String eventVisibility(GameEvent event) {
        return com.example.avalon.runtime.disclosure.GameEventVisibilityPolicy.visibility(event.type());
    }

    private void persistAudits(GameRuntimeState state) {
        if (auditRecordStore == null) {
            return;
        }
        for (RuntimeAuditEntry entry : state.auditEntries()) {
            auditRecordStore.save(toRecord(state.generatedGameId(), entry));
        }
        state.clearPendingAudits();
    }

    private AuditRecord toRecord(String gameId, RuntimeAuditEntry entry) {
        String inputContextJson = writeJson(entry.inputContext());
        return new AuditRecord(
                entry.auditId(),
                gameId,
                entry.eventSeqNo(),
                entry.playerId(),
                entry.visibility(),
                inputContextJson,
                sha256(inputContextJson),
                writeJson(entry.rawModelResponse()),
                writeJson(entry.parsedAction()),
                writeJson(entry.auditReason()),
                writeJson(entry.validationResult()),
                entry.errorMessage(),
                entry.createdAt()
        );
    }

    private String snapshotId(String gameId, long basedOnEventSeqNo) {
        return gameId + "-snapshot-" + basedOnEventSeqNo;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize persistence payload", e);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash audit input context", exception);
        }
    }
}
