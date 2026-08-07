package com.example.avalon.persistence.repository;

import com.example.avalon.persistence.entity.GameSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameSnapshotRepository extends JpaRepository<GameSnapshotEntity, String> {
    List<GameSnapshotEntity> findByGameIdOrderByBasedOnEventSeqNoAsc(String gameId);

    Optional<GameSnapshotEntity> findTopByGameIdOrderByBasedOnEventSeqNoDesc(String gameId);

    Optional<GameSnapshotEntity> findTopByGameIdAndBasedOnEventSeqNoLessThanEqualOrderByBasedOnEventSeqNoDesc(String gameId, Long basedOnEventSeqNo);

    Optional<GameSnapshotEntity> findTopByGameIdOrderByCreatedAtDesc(String gameId);
}
