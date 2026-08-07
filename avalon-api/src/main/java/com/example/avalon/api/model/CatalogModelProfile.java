package com.example.avalon.api.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record CatalogModelProfile(
        String modelId,
        String displayName,
        ModelProfileSource source,
        boolean editable,
        boolean enabled,
        String provider,
        String modelName,
        Double temperature,
        Integer maxTokens,
        Map<String, Object> providerOptions
) {
    public CatalogModelProfile {
        providerOptions = providerOptions == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(providerOptions));
    }
}
