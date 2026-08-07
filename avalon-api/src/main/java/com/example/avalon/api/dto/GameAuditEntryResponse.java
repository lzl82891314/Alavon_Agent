package com.example.avalon.api.dto;

import java.time.Instant;

public class GameAuditEntryResponse {
    private String auditId;
    private Long eventSeqNo;
    private String playerId;
    private String visibility;
    private String inputContextJson;
    private String rawModelResponseJson;
    private String parsedActionJson;
    private String auditReasonJson;
    private String validationResultJson;
    private String errorMessage;
    private Instant createdAt;

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
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
