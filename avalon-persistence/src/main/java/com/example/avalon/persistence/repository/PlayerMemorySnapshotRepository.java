package com.example.avalon.persistence.repository;

import com.example.avalon.persistence.entity.PlayerMemorySnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerMemorySnapshotRepository extends JpaRepository<PlayerMemorySnapshotEntity, String> {
    List<PlayerMemorySnapshotEntity> findByGameIdOrderByPlayerIdAscBasedOnEventSeqNoDesc(String gameId);

    List<PlayerMemorySnapshotEntity> findByGameIdAndPlayerIdOrderByBasedOnEventSeqNoDesc(String gameId, String playerId);

    Optional<PlayerMemorySnapshotEntity> findTopByGameIdAndPlayerIdOrderByBasedOnEventSeqNoDesc(String gameId, String playerId);

    Optional<PlayerMemorySnapshotEntity> findTopByGameIdAndPlayerIdAndBasedOnEventSeqNoLessThanEqualOrderByBasedOnEventSeqNoDesc(String gameId, String playerId, Long basedOnEventSeqNo);
}
