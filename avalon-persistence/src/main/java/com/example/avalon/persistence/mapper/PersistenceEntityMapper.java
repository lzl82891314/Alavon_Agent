package com.example.avalon.persistence.mapper;

import com.example.avalon.persistence.entity.AuditRecordEntity;
import com.example.avalon.persistence.entity.GameEventEntity;
import com.example.avalon.persistence.entity.GameSnapshotEntity;
import com.example.avalon.persistence.entity.ModelProfileEntity;
import com.example.avalon.persistence.entity.PlayerMemorySnapshotEntity;
import com.example.avalon.persistence.model.AuditRecord;
import com.example.avalon.persistence.model.GameEventRecord;
import com.example.avalon.persistence.model.GameSnapshotRecord;
import com.example.avalon.persistence.model.ModelProfileRecord;
import com.example.avalon.persistence.model.PlayerMemorySnapshotRecord;

public final class PersistenceEntityMapper {
    private PersistenceEntityMapper() {
    }

    public static GameEventRecord toRecord(GameEventEntity entity) {
        GameEventRecord record = new GameEventRecord(
                entity.getEventId(),
                entity.getGameId(),
                entity.getSeqNo(),
                entity.getType(),
                entity.getPhase(),
                entity.getActorPlayerId(),
                entity.getVisibility(),
                entity.getPayloadJson(),
                entity.getCreatedAt()
        );
        return record;
    }

    public static GameEventEntity toEntity(GameEventRecord record) {
        GameEventEntity entity = new GameEventEntity();
        entity.setEventId(record.eventId());
        entity.setGameId(record.gameId());
        entity.setSeqNo(record.seqNo());
        entity.setType(record.type());
        entity.setPhase(record.phase());
        entity.setActorPlayerId(record.actorPlayerId());
        entity.setVisibility(record.visibility());
        entity.setPayloadJson(record.payloadJson());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }

    public static GameSnapshotRecord toRecord(GameSnapshotEntity entity) {
        return new GameSnapshotRecord(
                entity.getSnapshotId(),
                entity.getGameId(),
                entity.getBasedOnEventSeqNo(),
                entity.getRoundNo(),
                entity.getPhase(),
                entity.getStateJson(),
                entity.getCreatedAt()
        );
    }

    public static GameSnapshotEntity toEntity(GameSnapshotRecord record) {
        GameSnapshotEntity entity = new GameSnapshotEntity();
        entity.setSnapshotId(record.snapshotId());
        entity.setGameId(record.gameId());
        entity.setBasedOnEventSeqNo(record.basedOnEventSeqNo());
        entity.setRoundNo(record.roundNo());
        entity.setPhase(record.phase());
        entity.setStateJson(record.stateJson());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }

    public static PlayerMemorySnapshotRecord toRecord(PlayerMemorySnapshotEntity entity) {
        return new PlayerMemorySnapshotRecord(
                entity.getSnapshotId(),
                entity.getGameId(),
                entity.getPlayerId(),
                entity.getBasedOnEventSeqNo(),
                entity.getMemoryJson(),
                entity.getCreatedAt()
        );
    }

    public static PlayerMemorySnapshotEntity toEntity(PlayerMemorySnapshotRecord record) {
        PlayerMemorySnapshotEntity entity = new PlayerMemorySnapshotEntity();
        entity.setSnapshotId(record.snapshotId());
        entity.setGameId(record.gameId());
        entity.setPlayerId(record.playerId());
        entity.setBasedOnEventSeqNo(record.basedOnEventSeqNo());
        entity.setMemoryJson(record.memoryJson());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }

    public static AuditRecord toRecord(AuditRecordEntity entity) {
        return new AuditRecord(
                entity.getAuditId(),
                entity.getGameId(),
                entity.getEventSeqNo(),
                entity.getPlayerId(),
                entity.getVisibility(),
                entity.getInputContextJson(),
                entity.getInputContextHash(),
                entity.getRawModelResponseJson(),
                entity.getParsedActionJson(),
                entity.getAuditReasonJson(),
                entity.getValidationResultJson(),
                entity.getErrorMessage(),
                entity.getCreatedAt()
        );
    }

    public static AuditRecordEntity toEntity(AuditRecord record) {
        AuditRecordEntity entity = new AuditRecordEntity();
        entity.setAuditId(record.auditId());
        entity.setGameId(record.gameId());
        entity.setEventSeqNo(record.eventSeqNo());
        entity.setPlayerId(record.playerId());
        entity.setVisibility(record.visibility());
        entity.setInputContextJson(record.inputContextJson());
        entity.setInputContextHash(record.inputContextHash());
        entity.setRawModelResponseJson(record.rawModelResponseJson());
        entity.setParsedActionJson(record.parsedActionJson());
        entity.setAuditReasonJson(record.auditReasonJson());
        entity.setValidationResultJson(record.validationResultJson());
        entity.setErrorMessage(record.errorMessage());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }

    public static ModelProfileRecord toRecord(ModelProfileEntity entity) {
        return new ModelProfileRecord(
                entity.getModelId(),
                entity.getDisplayName(),
                entity.getProvider(),
                entity.getModelName(),
                entity.getTemperature(),
                entity.getMaxTokens(),
                entity.getProviderOptionsJson(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static ModelProfileEntity toEntity(ModelProfileRecord record) {
        ModelProfileEntity entity = new ModelProfileEntity();
        entity.setModelId(record.modelId());
        entity.setDisplayName(record.displayName());
        entity.setProvider(record.provider());
        entity.setModelName(record.modelName());
        entity.setTemperature(record.temperature());
        entity.setMaxTokens(record.maxTokens());
        entity.setProviderOptionsJson(record.providerOptionsJson());
        entity.setEnabled(record.enabled());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        return entity;
    }
}
