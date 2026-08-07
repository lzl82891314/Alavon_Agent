package com.example.avalon.agent.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MemoryUpdate {
    private Map<String, Double> suspicionDelta = new LinkedHashMap<>();
    private Map<String, Double> trustDelta = new LinkedHashMap<>();
    private List<String> observationsToAdd = new ArrayList<>();
    private List<String> commitmentsToAdd = new ArrayList<>();
    private List<String> inferredFactsToAdd = new ArrayList<>();
    private String strategyMode;
    private String lastSummary;

    public Map<String, Double> getSuspicionDelta() {
        return suspicionDelta;
    }

    public void setSuspicionDelta(Map<String, Double> suspicionDelta) {
        this.suspicionDelta = suspicionDelta == null ? new LinkedHashMap<>() : new LinkedHashMap<>(suspicionDelta);
    }

    public Map<String, Double> getTrustDelta() {
        return trustDelta;
    }

    public void setTrustDelta(Map<String, Double> trustDelta) {
        this.trustDelta = trustDelta == null ? new LinkedHashMap<>() : new LinkedHashMap<>(trustDelta);
    }

    public List<String> getObservationsToAdd() {
        return observationsToAdd;
    }

    public void setObservationsToAdd(List<String> observationsToAdd) {
        this.observationsToAdd = observationsToAdd == null ? new ArrayList<>() : new ArrayList<>(observationsToAdd);
    }

    public List<String> getCommitmentsToAdd() {
        return commitmentsToAdd;
    }

    public void setCommitmentsToAdd(List<String> commitmentsToAdd) {
        this.commitmentsToAdd = commitmentsToAdd == null ? new ArrayList<>() : new ArrayList<>(commitmentsToAdd);
    }

    public List<String> getInferredFactsToAdd() {
        return inferredFactsToAdd;
    }

    public void setInferredFactsToAdd(List<String> inferredFactsToAdd) {
        this.inferredFactsToAdd = inferredFactsToAdd == null ? new ArrayList<>() : new ArrayList<>(inferredFactsToAdd);
    }

    public String getStrategyMode() {
        return strategyMode;
    }

    public void setStrategyMode(String strategyMode) {
        this.strategyMode = strategyMode;
    }

    public String getLastSummary() {
        return lastSummary;
    }

    public void setLastSummary(String lastSummary) {
        this.lastSummary = lastSummary;
    }
}
