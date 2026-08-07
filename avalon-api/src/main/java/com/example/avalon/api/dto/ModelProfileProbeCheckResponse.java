package com.example.avalon.api.dto;

public class ModelProfileProbeCheckResponse {
    private String checkType;
    private boolean success;
    private Integer httpStatus;
    private Long latencyMs;
    private String finishReason;
    private String assistantPreview;
    private Boolean contentPresent;
    private Boolean reasoningDetailsPresent;
    private String contentShape;
    private String reasoningDetailsPreview;
    private String errorMessage;

    public String getCheckType() {
        return checkType;
    }

    public void setCheckType(String checkType) {
        this.checkType = checkType;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public String getAssistantPreview() {
        return assistantPreview;
    }

    public void setAssistantPreview(String assistantPreview) {
        this.assistantPreview = assistantPreview;
    }

    public Boolean getContentPresent() {
        return contentPresent;
    }

    public void setContentPresent(Boolean contentPresent) {
        this.contentPresent = contentPresent;
    }

    public Boolean getReasoningDetailsPresent() {
        return reasoningDetailsPresent;
    }

    public void setReasoningDetailsPresent(Boolean reasoningDetailsPresent) {
        this.reasoningDetailsPresent = reasoningDetailsPresent;
    }

    public String getContentShape() {
        return contentShape;
    }

    public void setContentShape(String contentShape) {
        this.contentShape = contentShape;
    }

    public String getReasoningDetailsPreview() {
        return reasoningDetailsPreview;
    }

    public void setReasoningDetailsPreview(String reasoningDetailsPreview) {
        this.reasoningDetailsPreview = reasoningDetailsPreview;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
