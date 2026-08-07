package com.example.avalon.agent.model;

public class PlayerAgentConfig {
    private String playerId;
    private String promptProfileId;
    private String outputSchemaVersion;
    private String auditLevel;
    private ModelProfile modelProfile = new ModelProfile();

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

    public ModelProfile getModelProfile() {
        return modelProfile;
    }

    public void setModelProfile(ModelProfile modelProfile) {
        this.modelProfile = modelProfile == null ? new ModelProfile() : modelProfile;
    }
}

