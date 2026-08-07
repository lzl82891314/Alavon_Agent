package com.example.avalon.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModelProfile {
    private String modelId;
    private String provider;
    private String modelName;
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> providerOptions = new LinkedHashMap<>();

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

    public Map<String, Object> getProviderOptions() {
        return providerOptions;
    }

    public void setProviderOptions(Map<String, Object> providerOptions) {
        this.providerOptions = providerOptions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(providerOptions);
    }
}
