package com.example.avalon.agent.gateway;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ModelProtocolAdapterRegistry {
    private final Map<String, ModelProtocolAdapter> adapters;

    public ModelProtocolAdapterRegistry(List<ModelProtocolAdapter> adapters) {
        Map<String, ModelProtocolAdapter> resolved = new LinkedHashMap<>();
        for (ModelProtocolAdapter adapter : adapters) {
            String protocolId = normalize(adapter.protocolId());
            if (resolved.putIfAbsent(protocolId, adapter) != null) {
                throw new IllegalStateException("Duplicate model protocol adapter: " + protocolId);
            }
        }
        this.adapters = Map.copyOf(resolved);
    }

    public ModelProtocolAdapter require(String protocolId) {
        ModelProtocolAdapter adapter = adapters.get(normalize(protocolId));
        if (adapter == null) {
            throw new IllegalArgumentException("No model protocol adapter is registered for '" + protocolId + "'");
        }
        return adapter;
    }

    private String normalize(String protocolId) {
        if (protocolId == null || protocolId.isBlank()) {
            throw new IllegalArgumentException("model protocol must not be blank");
        }
        return protocolId.trim().toUpperCase(Locale.ROOT);
    }
}
