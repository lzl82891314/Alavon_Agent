package com.example.avalon.persistence.store;

import com.example.avalon.persistence.model.PlayerMemorySnapshotRecord;

import java.util.List;
import java.util.Optional;

public interface PlayerMemorySnapshotStore {
    PlayerMemorySnapshotRecord save(PlayerMemorySnapshotRecord record);

    List<PlayerMemorySnapshotRecord> findByGameId(String gameId);

    List<PlayerMemorySnapshotRecord> findByGameIdAndPlayerId(String gameId, String playerId);

    Optional<PlayerMemorySnapshotRecord> findLatestByGameIdAndPlayerId(String gameId, String playerId);

    Optional<PlayerMemorySnapshotRecord> findLatestAtOrBefore(String gameId, String playerId, long basedOnEventSeqNo);
}

