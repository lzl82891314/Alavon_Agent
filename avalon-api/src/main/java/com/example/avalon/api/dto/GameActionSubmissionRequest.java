package com.example.avalon.api.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class GameActionSubmissionRequest {
    private String actionType;
    private String idempotencyKey;
    private Map<String, Object> payload = new LinkedHashMap<>();

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }
}

