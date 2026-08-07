package com.example.avalon.api.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModelProfileRequest {
    private String modelId;
    private String displayName;
    private String provider;
    private String modelName;
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> providerOptions = new LinkedHashMap<>();
    private Boolean enabled;

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
