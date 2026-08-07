package com.example.avalon.agent.gateway;

import java.util.Map;

public interface ModelProfileApiKeyResolver {
    String resolveApiKey(String modelId, Map<String, Object> providerOptions);
}
