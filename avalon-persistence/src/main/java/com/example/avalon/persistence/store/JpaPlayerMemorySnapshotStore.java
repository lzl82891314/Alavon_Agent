package com.example.avalon.persistence.store;

import com.example.avalon.persistence.mapper.PersistenceEntityMapper;
import com.example.avalon.persistence.model.PlayerMemorySnapshotRecord;
import com.example.avalon.persistence.repository.PlayerMemorySnapshotRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JpaPlayerMemorySnapshotStore implements PlayerMemorySnapshotStore {
    private final PlayerMemorySnapshotRepository repository;

    public JpaPlayerMemorySnapshotStore(PlayerMemorySnapshotRepository repository) {
        this.repository = repository;
    }

    @Override
    public PlayerMemorySnapshotRecord save(PlayerMemorySnapshotRecord record) {
        return PersistenceEntityMapper.toRecord(repository.save(PersistenceEntityMapper.toEntity(record)));
    }

    @Override
    public List<PlayerMemorySnapshotRecord> findByGameId(String gameId) {
        return repository.findByGameIdOrderByPlayerIdAscBasedOnEventSeqNoDesc(gameId).stream()
                .map(PersistenceEntityMapper::toRecord)
                .toList();
    }

    @Override
    public List<PlayerMemorySnapshotRecord> findByGameIdAndPlayerId(String gameId, String playerId) {
        return repository.findByGameIdAndPlayerIdOrderByBasedOnEventSeqNoDesc(gameId, playerId).stream()
                .map(PersistenceEntityMapper::toRecord)
                .toList();
    }

    @Override
    public Optional<PlayerMemorySnapshotRecord> findLatestByGameIdAndPlayerId(String gameId, String playerId) {
        return repository.findTopByGameIdAndPlayerIdOrderByBasedOnEventSeqNoDesc(gameId, playerId)
                .map(PersistenceEntityMapper::toRecord);
    }

    @Override
    public Optional<PlayerMemorySnapshotRecord> findLatestAtOrBefore(String gameId, String playerId, long basedOnEventSeqNo) {
        return repository.findTopByGameIdAndPlayerIdAndBasedOnEventSeqNoLessThanEqualOrderByBasedOnEventSeqNoDesc(gameId, playerId, basedOnEventSeqNo)
                .map(PersistenceEntityMapper::toRecord);
    }
}

