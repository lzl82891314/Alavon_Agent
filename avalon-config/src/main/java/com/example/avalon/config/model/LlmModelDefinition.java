package com.example.avalon.config.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record LlmModelDefinition(
        String modelId,
        String displayName,
        String provider,
        String modelName,
        Double temperature,
        Integer maxTokens,
        Map<String, Object> providerOptions,
        boolean enabled
) {
    public LlmModelDefinition {
        providerOptions = providerOptions == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(providerOptions));
    }
}
