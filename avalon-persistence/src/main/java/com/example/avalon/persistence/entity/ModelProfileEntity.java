package com.example.avalon.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "model_profile", indexes = {
        @Index(name = "idx_model_profile_enabled_created", columnList = "enabled,created_at"),
        @Index(name = "idx_model_profile_provider_model", columnList = "provider,model_name")
})
public class ModelProfileEntity {
    @Id
    @Column(name = "model_id", nullable = false, length = 128)
    private String modelId;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "provider_options_json", columnDefinition = "TEXT")
    private String providerOptionsJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public String getProviderOptionsJson() {
        return providerOptionsJson;
    }

    public void setProviderOptionsJson(String providerOptionsJson) {
        this.providerOptionsJson = providerOptionsJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
