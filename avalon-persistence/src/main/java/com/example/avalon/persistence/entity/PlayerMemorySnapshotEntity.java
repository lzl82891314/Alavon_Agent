package com.example.avalon.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "player_memory_snapshot", indexes = {
        @Index(name = "idx_player_memory_game_player_seq", columnList = "game_id,player_id,based_on_event_seq_no", unique = true),
        @Index(name = "idx_player_memory_game_player_created", columnList = "game_id,player_id,created_at"),
        @Index(name = "idx_player_memory_game_created", columnList = "game_id,created_at")
})
public class PlayerMemorySnapshotEntity {
    @Id
    @Column(name = "snapshot_id", nullable = false, length = 64)
    private String snapshotId;

    @Column(name = "game_id", nullable = false, length = 64)
    private String gameId;

    @Column(name = "player_id", nullable = false, length = 64)
    private String playerId;

    @Column(name = "based_on_event_seq_no", nullable = false)
    private Long basedOnEventSeqNo;

    @Column(name = "memory_json", columnDefinition = "TEXT")
    private String memoryJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public Long getBasedOnEventSeqNo() {
        return basedOnEventSeqNo;
    }

    public void setBasedOnEventSeqNo(Long basedOnEventSeqNo) {
        this.basedOnEventSeqNo = basedOnEventSeqNo;
    }

    public String getMemoryJson() {
        return memoryJson;
    }

    public void setMemoryJson(String memoryJson) {
        this.memoryJson = memoryJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
