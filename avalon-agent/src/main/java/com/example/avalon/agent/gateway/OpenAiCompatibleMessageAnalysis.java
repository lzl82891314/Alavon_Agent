package com.example.avalon.agent.gateway;

import java.util.LinkedHashMap;
import java.util.Map;

public record OpenAiCompatibleMessageAnalysis(
        boolean contentPresent,
        boolean reasoningDetailsPresent,
        String assistantContentShape,
        String assistantContentPreview,
        String reasoningDetailsPreview,
        String jsonCandidate
) {
    public boolean hasJsonCandidate() {
        return jsonCandidate != null && !jsonCandidate.isBlank();
    }

    public Map<String, Object> diagnostics() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contentPresent", contentPresent);
        payload.put("reasoningDetailsPresent", reasoningDetailsPresent);
        putIfNotBlank(payload, "assistantContentShape", assistantContentShape);
        putIfNotBlank(payload, "assistantContentPreview", assistantContentPreview);
        putIfNotBlank(payload, "reasoningDetailsPreview", reasoningDetailsPreview);
        return payload;
    }

    private void putIfNotBlank(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }
}
