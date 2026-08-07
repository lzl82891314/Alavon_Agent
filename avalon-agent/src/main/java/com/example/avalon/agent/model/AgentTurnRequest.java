package com.example.avalon.agent.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentTurnRequest {
    private String gameId;
    private Integer roundNo;
    private String phase;
    private String playerId;
    private Integer seatNo;
    private String roleId;
    private String modelId;
    private String provider;
    private String modelName;
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> privateKnowledge = new LinkedHashMap<>();
    private Map<String, Object> publicState = new LinkedHashMap<>();
    private Map<String, Object> memory = new LinkedHashMap<>();
    private List<String> allowedActions = List.of();
    private String rulesSummary;
    private String outputSchemaVersion;
    private String promptText;
    private Map<String, Object> providerOptions = new LinkedHashMap<>();

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public Integer getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(Integer seatNo) {
        this.seatNo = seatNo;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Map<String, Object> getPrivateKnowledge() {
        return privateKnowledge;
    }

    public void setPrivateKnowledge(Map<String, Object> privateKnowledge) {
        this.privateKnowledge = privateKnowledge == null ? new LinkedHashMap<>() : new LinkedHashMap<>(privateKnowledge);
    }

    public Map<String, Object> getPublicState() {
        return publicState;
    }

    public void setPublicState(Map<String, Object> publicState) {
        this.publicState = publicState == null ? new LinkedHashMap<>() : new LinkedHashMap<>(publicState);
    }

    public Map<String, Object> getMemory() {
        return memory;
    }

    public void setMemory(Map<String, Object> memory) {
        this.memory = memory == null ? new LinkedHashMap<>() : new LinkedHashMap<>(memory);
    }

    public List<String> getAllowedActions() {
        return allowedActions;
    }

    public void setAllowedActions(List<String> allowedActions) {
        this.allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    public String getRulesSummary() {
        return rulesSummary;
    }

    public void setRulesSummary(String rulesSummary) {
        this.rulesSummary = rulesSummary;
    }

    public String getOutputSchemaVersion() {
        return outputSchemaVersion;
    }

    public void setOutputSchemaVersion(String outputSchemaVersion) {
        this.outputSchemaVersion = outputSchemaVersion;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public Map<String, Object> getProviderOptions() {
        return providerOptions;
    }

    public void setProviderOptions(Map<String, Object> providerOptions) {
        this.providerOptions = providerOptions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(providerOptions);
    }

    public AgentTurnRequest copy() {
        AgentTurnRequest copy = new AgentTurnRequest();
        copy.setGameId(gameId);
        copy.setRoundNo(roundNo);
        copy.setPhase(phase);
        copy.setPlayerId(playerId);
        copy.setSeatNo(seatNo);
        copy.setRoleId(roleId);
        copy.setModelId(modelId);
        copy.setProvider(provider);
        copy.setModelName(modelName);
        copy.setTemperature(temperature);
        copy.setMaxTokens(maxTokens);
        copy.setPrivateKnowledge(privateKnowledge);
        copy.setPublicState(publicState);
        copy.setMemory(memory);
        copy.setAllowedActions(allowedActions);
        copy.setRulesSummary(rulesSummary);
        copy.setOutputSchemaVersion(outputSchemaVersion);
        copy.setPromptText(promptText);
        copy.setProviderOptions(providerOptions);
        return copy;
    }
}
