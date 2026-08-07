package com.example.avalon.persistence.store;

import com.example.avalon.persistence.model.AuditRecord;

import java.util.List;
import java.util.Optional;

public interface AuditRecordStore {
    AuditRecord save(AuditRecord record);

    List<AuditRecord> findByGameId(String gameId);

    List<AuditRecord> findByGameIdAndPlayerId(String gameId, String playerId);

    List<AuditRecord> findByGameIdAndEventSeqNo(String gameId, long eventSeqNo);

    Optional<AuditRecord> findLatestByGameId(String gameId);
}

