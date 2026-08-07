package com.example.avalon.persistence.store;

import com.example.avalon.persistence.mapper.PersistenceEntityMapper;
import com.example.avalon.persistence.model.GameSnapshotRecord;
import com.example.avalon.persistence.repository.GameSnapshotRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JpaGameSnapshotStore implements GameSnapshotStore {
    private final GameSnapshotRepository repository;

    public JpaGameSnapshotStore(GameSnapshotRepository repository) {
        this.repository = repository;
    }

    @Override
    public GameSnapshotRecord save(GameSnapshotRecord record) {
        return PersistenceEntityMapper.toRecord(repository.save(PersistenceEntityMapper.toEntity(record)));
    }

    @Override
    public List<GameSnapshotRecord> findByGameId(String gameId) {
        return repository.findByGameIdOrderByBasedOnEventSeqNoAsc(gameId).stream()
                .map(PersistenceEntityMapper::toRecord)
                .toList();
    }

    @Override
    public Optional<GameSnapshotRecord> findLatestByGameId(String gameId) {
        return repository.findTopByGameIdOrderByBasedOnEventSeqNoDesc(gameId).map(PersistenceEntityMapper::toRecord);
    }

    @Override
    public Optional<GameSnapshotRecord> findLatestAtOrBefore(String gameId, long basedOnEventSeqNo) {
        return repository.findTopByGameIdAndBasedOnEventSeqNoLessThanEqualOrderByBasedOnEventSeqNoDesc(gameId, basedOnEventSeqNo)
                .map(PersistenceEntityMapper::toRecord);
    }
}

