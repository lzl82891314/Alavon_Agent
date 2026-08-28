package com.example.avalon.agent.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolCall(String callId, String toolName, Map<String, Object> arguments) {
    public ToolCall {
        if (callId == null || callId.isBlank()) throw new IllegalArgumentException("Tool call id is required");
        if (toolName == null || toolName.isBlank()) throw new IllegalArgumentException("Tool name is required");
        arguments = arguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(arguments));
    }
}
