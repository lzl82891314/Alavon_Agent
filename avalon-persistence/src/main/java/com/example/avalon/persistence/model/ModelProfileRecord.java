package com.example.avalon.persistence.model;

import java.time.Instant;

public record ModelProfileRecord(
        String modelId,
        String displayName,
        String provider,
        String modelName,
        Double temperature,
        Integer maxTokens,
        String providerOptionsJson,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public ModelProfileRecord {
        providerOptionsJson = providerOptionsJson == null ? "{}" : providerOptionsJson;
    }
}
