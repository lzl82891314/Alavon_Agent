package com.example.avalon.persistence.model;

import java.time.Instant;

public record ModelProfileRecord(
        String modelId,
        String displayName,
        String provider,
        String protocol,
        String modelName,
        Double temperature,
        String providerOptionsJson,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public ModelProfileRecord {
        providerOptionsJson = providerOptionsJson == null ? "{}" : providerOptionsJson;
    }
}
