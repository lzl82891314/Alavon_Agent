package com.example.avalon.persistence.store;

import com.example.avalon.persistence.mapper.PersistenceEntityMapper;
import com.example.avalon.persistence.model.GameEventRecord;
import com.example.avalon.persistence.repository.GameEventRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JpaGameEventStore implements GameEventStore {
    private final GameEventRepository repository;

    public JpaGameEventStore(GameEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public GameEventRecord save(GameEventRecord record) {
        return PersistenceEntityMapper.toRecord(repository.save(PersistenceEntityMapper.toEntity(record)));
    }

    @Override
    public List<GameEventRecord> findByGameId(String gameId) {
        return repository.findByGameIdOrderBySeqNoAsc(gameId).stream()
                .map(PersistenceEntityMapper::toRecord)
                .toList();
    }

    @Override
    public List<GameEventRecord> findByGameIdAfterSeqNo(String gameId, long seqNo) {
        return repository.findByGameIdAndSeqNoGreaterThanOrderBySeqNoAsc(gameId, seqNo).stream()
                .map(PersistenceEntityMapper::toRecord)
                .toList();
    }

    @Override
    public List<GameEventRecord> findByGameIdAndType(String gameId, String type) {
        return repository.findByGameIdAndTypeOrderBySeqNoAsc(gameId, type).stream()
                .map(PersistenceEntityMapper::toRecord)
                .toList();
    }

    @Override
    public List<GameEventRecord> findByGameIdAndActorPlayerId(String gameId, String actorPlayerId) {
        return repository.findByGameIdAndActorPlayerIdOrderBySeqNoAsc(gameId, actorPlayerId).stream()
                .map(PersistenceEntityMapper::toRecord)
                .toList();
    }

    @Override
    public Optional<GameEventRecord> findLatestByGameId(String gameId) {
        return repository.findTopByGameIdOrderBySeqNoDesc(gameId).map(PersistenceEntityMapper::toRecord);
    }

    @Override
    public Optional<GameEventRecord> findLatestAtOrBefore(String gameId, long seqNo) {
        return repository.findTopByGameIdAndSeqNoLessThanEqualOrderBySeqNoDesc(gameId, seqNo)
                .map(PersistenceEntityMapper::toRecord);
    }

    @Override
    public long countByGameId(String gameId) {
        return repository.countByGameId(gameId);
    }
}

