package com.example.avalon.agent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentTurnResult {
    private String publicSpeech;
    private String privateThought;
    private String actionJson;
    private AuditReason auditReason;
    private MemoryUpdate memoryUpdate;
    private Map<String, Map<String, Object>> cognitionSectionStatuses = new LinkedHashMap<>();
    private List<String> acceptedCognitionSections = new ArrayList<>();
    private boolean cognitionDegraded;
    private Map<String, Object> privateActionAssessment = new LinkedHashMap<>();
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

    public Map<String, Map<String, Object>> getCognitionSectionStatuses() {
        return cognitionSectionStatuses;
    }

    public void setCognitionSectionStatuses(Map<String, Map<String, Object>> value) {
        cognitionSectionStatuses = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    public List<String> getAcceptedCognitionSections() {
        return acceptedCognitionSections;
    }

    public void setAcceptedCognitionSections(List<String> value) {
        acceptedCognitionSections = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }

    public boolean isCognitionDegraded() {
        return cognitionDegraded;
    }

    public void setCognitionDegraded(boolean cognitionDegraded) {
        this.cognitionDegraded = cognitionDegraded;
    }

    public Map<String, Object> getPrivateActionAssessment() {
        return privateActionAssessment;
    }

    public void setPrivateActionAssessment(Map<String, Object> value) {
        privateActionAssessment = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    public RawCompletionMetadata getModelMetadata() {
        return modelMetadata;
    }

    public void setModelMetadata(RawCompletionMetadata modelMetadata) {
        this.modelMetadata = modelMetadata == null ? new RawCompletionMetadata() : modelMetadata;
    }
}
