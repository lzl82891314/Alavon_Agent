package com.example.avalon.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_record", indexes = {
        @Index(name = "idx_audit_game_created", columnList = "game_id,created_at"),
        @Index(name = "idx_audit_game_event", columnList = "game_id,event_seq_no"),
        @Index(name = "idx_audit_game_player_created", columnList = "game_id,player_id,created_at"),
        @Index(name = "idx_audit_game_visibility_created", columnList = "game_id,visibility,created_at")
})
public class AuditRecordEntity {
    @Id
    @Column(name = "audit_id", nullable = false, length = 64)
    private String auditId;

    @Column(name = "game_id", nullable = false, length = 64)
    private String gameId;

    @Column(name = "event_seq_no")
    private Long eventSeqNo;

    @Column(name = "player_id", length = 64)
    private String playerId;

    @Column(name = "visibility", length = 32)
    private String visibility;

    @Column(name = "input_context_json", columnDefinition = "TEXT")
    private String inputContextJson;

    @Column(name = "input_context_hash", length = 128)
    private String inputContextHash;

    @Column(name = "raw_model_response_json", columnDefinition = "TEXT")
    private String rawModelResponseJson;

    @Column(name = "parsed_action_json", columnDefinition = "TEXT")
    private String parsedActionJson;

    @Column(name = "audit_reason_json", columnDefinition = "TEXT")
    private String auditReasonJson;

    @Column(name = "validation_result_json", columnDefinition = "TEXT")
    private String validationResultJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public Long getEventSeqNo() {
        return eventSeqNo;
    }

    public void setEventSeqNo(Long eventSeqNo) {
        this.eventSeqNo = eventSeqNo;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getInputContextJson() {
        return inputContextJson;
    }

    public void setInputContextJson(String inputContextJson) {
        this.inputContextJson = inputContextJson;
    }

    public String getInputContextHash() {
        return inputContextHash;
    }

    public void setInputContextHash(String inputContextHash) {
        this.inputContextHash = inputContextHash;
    }

    public String getRawModelResponseJson() {
        return rawModelResponseJson;
    }

    public void setRawModelResponseJson(String rawModelResponseJson) {
        this.rawModelResponseJson = rawModelResponseJson;
    }

    public String getParsedActionJson() {
        return parsedActionJson;
    }

    public void setParsedActionJson(String parsedActionJson) {
        this.parsedActionJson = parsedActionJson;
    }

    public String getAuditReasonJson() {
        return auditReasonJson;
    }

    public void setAuditReasonJson(String auditReasonJson) {
        this.auditReasonJson = auditReasonJson;
    }

    public String getValidationResultJson() {
        return validationResultJson;
    }

    public void setValidationResultJson(String validationResultJson) {
        this.validationResultJson = validationResultJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
