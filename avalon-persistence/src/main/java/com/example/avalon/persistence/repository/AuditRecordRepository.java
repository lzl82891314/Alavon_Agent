package com.example.avalon.persistence.repository;

import com.example.avalon.persistence.entity.AuditRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, String> {
    List<AuditRecordEntity> findByGameIdOrderByCreatedAtAsc(String gameId);

    List<AuditRecordEntity> findByGameIdAndPlayerIdOrderByCreatedAtAsc(String gameId, String playerId);

    List<AuditRecordEntity> findByGameIdAndEventSeqNoOrderByCreatedAtAsc(String gameId, Long eventSeqNo);

    List<AuditRecordEntity> findByGameIdAndVisibilityOrderByCreatedAtAsc(String gameId, String visibility);

    Optional<AuditRecordEntity> findTopByGameIdOrderByCreatedAtDesc(String gameId);
}
