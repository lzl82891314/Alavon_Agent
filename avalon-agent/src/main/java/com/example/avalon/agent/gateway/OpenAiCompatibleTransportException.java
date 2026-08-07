package com.example.avalon.agent.gateway;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenAiCompatibleTransportException extends IllegalStateException {
    private final Map<String, Object> diagnostics;

    public OpenAiCompatibleTransportException(String message,
                                              Throwable cause,
                                              Map<String, Object> diagnostics) {
        super(message, cause);
        this.diagnostics = diagnostics == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(diagnostics));
    }

    public Map<String, Object> diagnostics() {
        return diagnostics;
    }
}
