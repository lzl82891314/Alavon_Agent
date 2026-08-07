package com.example.avalon.agent.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModelProfileSecretSupport {
    public static final String DEFAULT_SHARED_ENV_VAR = "OPENAI_API_KEY";
    private static final String SHARED_PROPERTY_PREFIX = "avalon.model-profile-api-keys.";
    private static final String SHARED_ENV_PREFIX = "AVALON_MODEL_PROFILE_API_KEY_";

    private ModelProfileSecretSupport() {
    }

    public static String dedicatedPropertyName(String modelId) {
        return SHARED_PROPERTY_PREFIX + modelId;
    }

    public static String dedicatedEnvironmentVariableName(String modelId) {
        return SHARED_ENV_PREFIX + sanitizeModelId(modelId);
    }

    public static String sanitizeModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return "MODEL_ID";
        }
        StringBuilder builder = new StringBuilder();
        boolean underscorePending = false;
        for (char current : modelId.toCharArray()) {
            if (Character.isLetterOrDigit(current)) {
                if (underscorePending && !builder.isEmpty()) {
                    builder.append('_');
                }
                builder.append(Character.toUpperCase(current));
                underscorePending = false;
                continue;
            }
            underscorePending = true;
        }
        String sanitized = builder.toString();
        return sanitized.isBlank() ? "MODEL_ID" : sanitized;
    }

    public static String missingApiKeyMessage(String providerId, String modelId) {
        List<String> sources = new ArrayList<>();
        sources.add("providerOptions.apiKey");
        if (modelId != null && !modelId.isBlank()) {
            sources.add("avalon-model-profile-secrets.yml -> modelProfileApiKeys." + modelId);
            sources.add(dedicatedPropertyName(modelId));
            sources.add(dedicatedEnvironmentVariableName(modelId));
        }
        sources.add("providerOptions.apiKeyEnv");
        sources.add(DEFAULT_SHARED_ENV_VAR);
        return "OpenAI-compatible provider '" + normalizeProviderId(providerId)
                + "' requires an API key via " + String.join(", ", sources);
    }

    private static String normalizeProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return "openai";
        }
        return providerId.trim().toLowerCase(Locale.ROOT);
    }
}
