package com.example.avalon.agent.model;

import com.example.avalon.core.player.memory.BehaviorPrediction;
import com.example.avalon.core.player.memory.EvidenceAssessment;
import com.example.avalon.core.player.memory.PossibleWorld;
import com.example.avalon.core.player.memory.StrategicActionCandidate;

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
    private List<Map<String, Object>> worldFactsToAdd = new ArrayList<>();
    private List<Map<String, Object>> publicClaimsToAdd = new ArrayList<>();
    private Map<String, Double> roleBeliefs = new LinkedHashMap<>();
    private Map<String, Object> strategyState = new LinkedHashMap<>();
    private Map<String, Object> communicationPlan = new LinkedHashMap<>();
    private List<Long> evidenceReferences = new ArrayList<>();
    private Map<String, List<Long>> beliefEvidenceReferences = new LinkedHashMap<>();
    private Long observedThroughSequence;
    private String strategyMode;
    private String lastSummary;
    private List<PossibleWorld> worldHypotheses = new ArrayList<>();
    private List<BehaviorPrediction> activePredictions = new ArrayList<>();
    private List<EvidenceAssessment> evidenceAssessments = new ArrayList<>();
    private List<StrategicActionCandidate> actionAssessments = new ArrayList<>();

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

    public List<Map<String, Object>> getWorldFactsToAdd() { return worldFactsToAdd; }
    public void setWorldFactsToAdd(List<Map<String, Object>> value) { worldFactsToAdd = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public List<Map<String, Object>> getPublicClaimsToAdd() { return publicClaimsToAdd; }
    public void setPublicClaimsToAdd(List<Map<String, Object>> value) { publicClaimsToAdd = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public Map<String, Double> getRoleBeliefs() { return roleBeliefs; }
    public void setRoleBeliefs(Map<String, Double> value) { roleBeliefs = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value); }
    public Map<String, Object> getStrategyState() { return strategyState; }
    public void setStrategyState(Map<String, Object> value) { strategyState = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value); }
    public Map<String, Object> getCommunicationPlan() { return communicationPlan; }
    public void setCommunicationPlan(Map<String, Object> value) { communicationPlan = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value); }
    public List<Long> getEvidenceReferences() { return evidenceReferences; }
    public void setEvidenceReferences(List<Long> value) { evidenceReferences = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public Map<String, List<Long>> getBeliefEvidenceReferences() { return beliefEvidenceReferences; }
    public void setBeliefEvidenceReferences(Map<String, List<Long>> value) {
        beliefEvidenceReferences = new LinkedHashMap<>();
        if (value != null) value.forEach((playerId, references) ->
                beliefEvidenceReferences.put(playerId, references == null ? new ArrayList<>() : new ArrayList<>(references)));
    }
    public Long getObservedThroughSequence() { return observedThroughSequence; }
    public void setObservedThroughSequence(Long value) { observedThroughSequence = value; }

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

    public List<PossibleWorld> getWorldHypotheses() { return worldHypotheses; }
    public void setWorldHypotheses(List<PossibleWorld> value) { worldHypotheses = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public List<BehaviorPrediction> getActivePredictions() { return activePredictions; }
    public void setActivePredictions(List<BehaviorPrediction> value) { activePredictions = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public List<EvidenceAssessment> getEvidenceAssessments() { return evidenceAssessments; }
    public void setEvidenceAssessments(List<EvidenceAssessment> value) { evidenceAssessments = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public List<StrategicActionCandidate> getActionAssessments() { return actionAssessments; }
    public void setActionAssessments(List<StrategicActionCandidate> value) { actionAssessments = value == null ? new ArrayList<>() : new ArrayList<>(value); }
}
