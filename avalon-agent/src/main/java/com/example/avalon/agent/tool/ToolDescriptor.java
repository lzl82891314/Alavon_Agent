package com.example.avalon.agent.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolDescriptor(
        String name,
        String description,
        Map<String, Object> inputSchema,
        boolean readOnly,
        ToolResultVisibility resultVisibility
) {
    public ToolDescriptor {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Tool name is required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Tool description is required");
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(inputSchema));
        resultVisibility = resultVisibility == null ? ToolResultVisibility.AGENT_PRIVATE : resultVisibility;
    }
}
