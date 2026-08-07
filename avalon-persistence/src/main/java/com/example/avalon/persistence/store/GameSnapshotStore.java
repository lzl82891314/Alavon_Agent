package com.example.avalon.persistence.store;

import com.example.avalon.persistence.model.GameSnapshotRecord;

import java.util.List;
import java.util.Optional;

public interface GameSnapshotStore {
    GameSnapshotRecord save(GameSnapshotRecord record);

    List<GameSnapshotRecord> findByGameId(String gameId);

    Optional<GameSnapshotRecord> findLatestByGameId(String gameId);

    Optional<GameSnapshotRecord> findLatestAtOrBefore(String gameId, long basedOnEventSeqNo);
}

