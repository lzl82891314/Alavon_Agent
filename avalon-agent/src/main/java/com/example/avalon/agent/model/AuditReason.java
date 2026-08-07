package com.example.avalon.agent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuditReason {
    private String goal;
    private List<String> reasonSummary = new ArrayList<>();
    private Double confidence;
    private Map<String, Object> beliefs = new LinkedHashMap<>();

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public List<String> getReasonSummary() {
        return reasonSummary;
    }

    public void setReasonSummary(List<String> reasonSummary) {
        this.reasonSummary = reasonSummary == null ? new ArrayList<>() : new ArrayList<>(reasonSummary);
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Map<String, Object> getBeliefs() {
        return beliefs;
    }

    public void setBeliefs(Map<String, Object> beliefs) {
        this.beliefs = beliefs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(beliefs);
    }
}
