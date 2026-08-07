package com.example.avalon.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "game_snapshot", indexes = {
        @Index(name = "idx_game_snapshot_game_seq", columnList = "game_id,based_on_event_seq_no", unique = true),
        @Index(name = "idx_game_snapshot_game_round", columnList = "game_id,round_no"),
        @Index(name = "idx_game_snapshot_game_created", columnList = "game_id,created_at")
})
public class GameSnapshotEntity {
    @Id
    @Column(name = "snapshot_id", nullable = false, length = 64)
    private String snapshotId;

    @Column(name = "game_id", nullable = false, length = 64)
    private String gameId;

    @Column(name = "based_on_event_seq_no", nullable = false)
    private Long basedOnEventSeqNo;

    @Column(name = "round_no")
    private Integer roundNo;

    @Column(name = "phase", length = 64)
    private String phase;

    @Column(name = "state_json", columnDefinition = "TEXT")
    private String stateJson;

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

    public Long getBasedOnEventSeqNo() {
        return basedOnEventSeqNo;
    }

    public void setBasedOnEventSeqNo(Long basedOnEventSeqNo) {
        this.basedOnEventSeqNo = basedOnEventSeqNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getStateJson() {
        return stateJson;
    }

    public void setStateJson(String stateJson) {
        this.stateJson = stateJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
