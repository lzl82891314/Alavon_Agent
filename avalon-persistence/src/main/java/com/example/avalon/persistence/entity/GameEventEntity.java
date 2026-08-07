package com.example.avalon.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "game_event", indexes = {
        @Index(name = "idx_game_event_game_seq", columnList = "game_id,seq_no", unique = true),
        @Index(name = "idx_game_event_game_type_seq", columnList = "game_id,type,seq_no"),
        @Index(name = "idx_game_event_game_actor_seq", columnList = "game_id,actor_player_id,seq_no"),
        @Index(name = "idx_game_event_game_created", columnList = "game_id,created_at")
})
public class GameEventEntity {
    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "game_id", nullable = false, length = 64)
    private String gameId;

    @Column(name = "seq_no", nullable = false)
    private Long seqNo;

    @Column(name = "type", nullable = false, length = 128)
    private String type;

    @Column(name = "phase", length = 64)
    private String phase;

    @Column(name = "actor_player_id", length = 64)
    private String actorPlayerId;

    @Column(name = "visibility", length = 32)
    private String visibility;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public Long getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Long seqNo) {
        this.seqNo = seqNo;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getActorPlayerId() {
        return actorPlayerId;
    }

    public void setActorPlayerId(String actorPlayerId) {
        this.actorPlayerId = actorPlayerId;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
