package com.example.avalon.core.player.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerActionGenerationException extends RuntimeException {
    private final Map<String, Object> rawMetadata;

    public PlayerActionGenerationException(String message, Map<String, Object> rawMetadata, Throwable cause) {
        super(message, cause);
        this.rawMetadata = rawMetadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(rawMetadata));
    }

    public Map<String, Object> rawMetadata() {
        return rawMetadata;
    }
}
