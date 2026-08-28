package com.example.avalon.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ToolResult(
        String callId,
        String toolName,
        ToolExecutionStatus status,
        Map<String, Object> content,
        List<Long> sourceSequences,
        String errorType,
        String errorMessage,
        long durationMillis
) {
    public ToolResult {
        content = content == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(content));
        sourceSequences = sourceSequences == null ? List.of() : sourceSequences.stream().distinct().sorted().toList();
    }

    public static ToolResult success(ToolCall call, Map<String, Object> content, List<Long> sources) {
        return new ToolResult(call.callId(), call.toolName(), ToolExecutionStatus.SUCCEEDED, content, sources,
                null, null, 0L);
    }

    public ToolResult withDuration(long millis) {
        return new ToolResult(callId, toolName, status, content, sourceSequences, errorType, errorMessage, millis);
    }
}
