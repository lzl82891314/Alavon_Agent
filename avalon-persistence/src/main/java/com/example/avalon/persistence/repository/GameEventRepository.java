package com.example.avalon.persistence.repository;

import com.example.avalon.persistence.entity.GameEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameEventRepository extends JpaRepository<GameEventEntity, String> {
    List<GameEventEntity> findByGameIdOrderBySeqNoAsc(String gameId);

    List<GameEventEntity> findByGameIdAndSeqNoGreaterThanOrderBySeqNoAsc(String gameId, Long seqNo);

    List<GameEventEntity> findByGameIdAndTypeOrderBySeqNoAsc(String gameId, String type);

    List<GameEventEntity> findByGameIdAndActorPlayerIdOrderBySeqNoAsc(String gameId, String actorPlayerId);

    Optional<GameEventEntity> findTopByGameIdOrderBySeqNoDesc(String gameId);

    Optional<GameEventEntity> findTopByGameIdAndSeqNoLessThanEqualOrderBySeqNoDesc(String gameId, Long seqNo);

    long countByGameId(String gameId);
}
