package com.example.avalon.persistence.store;

import com.example.avalon.persistence.mapper.PersistenceEntityMapper;
import com.example.avalon.persistence.model.AuditRecord;
import com.example.avalon.persistence.repository.AuditRecordRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JpaAuditRecordStore implements AuditRecordStore {
    private final AuditRecordRepository repository;

    public JpaAuditRecordStore(AuditRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditRecord save(AuditRecord record) {
        return PersistenceEntityMapper.toRecord(repository.save(PersistenceEntityMapper.toEntity(record)));
    }

    @Override
    public List<AuditRecord> findByGameId(String gameId) {
        return repository.findByGameIdOrderByCreatedAtAsc(gameId).stream()
                .map(PersistenceEntityMapper::toRecord)
                .toList();
    }

    @Override
    public List<AuditRecord> findByGameIdAndPlayerId(String gameId, String playerId) {
        return repository.findByGameIdAndPlayerIdOrderByCreatedAtAsc(gameId, playerId).stream()
                .map(PersistenceEntityMapper::toRecord)
                .toList();
    }

    @Override
    public List<AuditRecord> findByGameIdAndEventSeqNo(String gameId, long eventSeqNo) {
        return repository.findByGameIdAndEventSeqNoOrderByCreatedAtAsc(gameId, eventSeqNo).stream()
                .map(PersistenceEntityMapper::toRecord)
                .toList();
    }

    @Override
    public Optional<AuditRecord> findLatestByGameId(String gameId) {
        return repository.findTopByGameIdOrderByCreatedAtDesc(gameId).map(PersistenceEntityMapper::toRecord);
    }
}
