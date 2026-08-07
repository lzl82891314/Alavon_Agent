package com.example.avalon.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class RawCompletionMetadata {
    private String provider;
    private String modelName;
    private Long inputTokens;
    private Long outputTokens;
    private Map<String, Object> attributes = new LinkedHashMap<>();

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

    public Long getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Long inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Long outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }
}

