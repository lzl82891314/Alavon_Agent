package com.example.avalon.agent.model;

public class AgentTurnResult {
    private String publicSpeech;
    private String privateThought;
    private String actionJson;
    private AuditReason auditReason;
    private MemoryUpdate memoryUpdate;
    private RawCompletionMetadata modelMetadata = new RawCompletionMetadata();

    public static AgentTurnResult empty() {
        return new AgentTurnResult();
    }

    public String getPublicSpeech() {
        return publicSpeech;
    }

    public void setPublicSpeech(String publicSpeech) {
        this.publicSpeech = publicSpeech;
    }

    public String getPrivateThought() {
        return privateThought;
    }

    public void setPrivateThought(String privateThought) {
        this.privateThought = privateThought;
    }

    public String getActionJson() {
        return actionJson;
    }

    public void setActionJson(String actionJson) {
        this.actionJson = actionJson;
    }

    public AuditReason getAuditReason() {
        return auditReason;
    }

    public void setAuditReason(AuditReason auditReason) {
        this.auditReason = auditReason;
    }

    public MemoryUpdate getMemoryUpdate() {
        return memoryUpdate;
    }

    public void setMemoryUpdate(MemoryUpdate memoryUpdate) {
        this.memoryUpdate = memoryUpdate;
    }

    public RawCompletionMetadata getModelMetadata() {
        return modelMetadata;
    }

    public void setModelMetadata(RawCompletionMetadata modelMetadata) {
        this.modelMetadata = modelMetadata == null ? new RawCompletionMetadata() : modelMetadata;
    }
}
