package com.example.avalon.api.dto;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class GameEventEntryResponse {
    private Long seqNo;
    private String type;
    private String phase;
    private String actorId;
    private String visibility;
    private String replayKind;
    private String summary;
    private Map<String, Object> payload = new LinkedHashMap<>();
    private Instant createdAt;

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

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getReplayKind() {
        return replayKind;
    }

    public void setReplayKind(String replayKind) {
        this.replayKind = replayKind;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
