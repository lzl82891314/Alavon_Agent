package com.example.avalon.agent.model;

import com.example.avalon.agent.harness.AgentHarnessType;

import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerAgentConfig {
    private String playerId;
    private String promptProfileId;
    private String outputSchemaVersion;
    private String auditLevel;
    private AgentHarnessType harnessType = AgentHarnessType.TOOL_CALLING;
    private ModelProfile modelProfile = new ModelProfile();
    private Map<String, Object> cognition = new LinkedHashMap<>();
    private Map<String, Object> communication = new LinkedHashMap<>();
    private Map<String, Object> deception = new LinkedHashMap<>();

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getPromptProfileId() {
        return promptProfileId;
    }

    public void setPromptProfileId(String promptProfileId) {
        this.promptProfileId = promptProfileId;
    }

    public String getOutputSchemaVersion() {
        return outputSchemaVersion;
    }

    public void setOutputSchemaVersion(String outputSchemaVersion) {
        this.outputSchemaVersion = outputSchemaVersion;
    }

    public String getAuditLevel() {
        return auditLevel;
    }

    public void setAuditLevel(String auditLevel) {
        this.auditLevel = auditLevel;
    }

    public AgentHarnessType getHarnessType() {
        return harnessType;
    }

    public void setHarnessType(AgentHarnessType harnessType) {
        this.harnessType = harnessType == null ? AgentHarnessType.TOOL_CALLING : harnessType;
    }

    public ModelProfile getModelProfile() {
        return modelProfile;
    }

    public void setModelProfile(ModelProfile modelProfile) {
        this.modelProfile = modelProfile == null ? new ModelProfile() : modelProfile;
    }

    public Map<String, Object> getCognition() { return cognition; }
    public void setCognition(Map<String, Object> value) { cognition = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value); }
    public Map<String, Object> getCommunication() { return communication; }
    public void setCommunication(Map<String, Object> value) { communication = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value); }
    public Map<String, Object> getDeception() { return deception; }
    public void setDeception(Map<String, Object> value) { deception = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value); }
}

