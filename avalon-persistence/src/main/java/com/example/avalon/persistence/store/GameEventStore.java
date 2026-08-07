package com.example.avalon.persistence.store;

import com.example.avalon.persistence.model.GameEventRecord;

import java.util.List;
import java.util.Optional;

public interface GameEventStore {
    GameEventRecord save(GameEventRecord record);

    List<GameEventRecord> findByGameId(String gameId);

    List<GameEventRecord> findByGameIdAfterSeqNo(String gameId, long seqNo);

    List<GameEventRecord> findByGameIdAndType(String gameId, String type);

    List<GameEventRecord> findByGameIdAndActorPlayerId(String gameId, String actorPlayerId);

    Optional<GameEventRecord> findLatestByGameId(String gameId);

    Optional<GameEventRecord> findLatestAtOrBefore(String gameId, long seqNo);

    long countByGameId(String gameId);
}

