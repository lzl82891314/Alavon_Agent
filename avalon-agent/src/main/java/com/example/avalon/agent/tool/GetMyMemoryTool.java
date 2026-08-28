package com.example.avalon.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class GetMyMemoryTool implements AgentTool {
    private static final ToolDescriptor DESCRIPTOR = StrategicToolSupport.descriptor(
            "get_my_memory",
            "Read this agent's host-projected private structured memory. Another player's memory cannot be selected.",
            Map.of("topic", Map.of("type", "string")));

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, ToolCall call) {
        StrategicToolSupport.rejectUnknownArguments(call.arguments(), "topic");
        String topic = StrategicToolSupport.optionalString(call.arguments(), "topic");
        Map<String, Object> memory = context.request().getMemory();
        Map<String, Object> selected = new LinkedHashMap<>();
        if (topic == null) {
            selected.putAll(memory);
        } else {
            memory.forEach((key, value) -> {
                if (key.toLowerCase(java.util.Locale.ROOT).contains(topic.toLowerCase(java.util.Locale.ROOT))) {
                    selected.put(key, value);
                }
            });
        }
        List<Long> sources = collectSources(selected);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("ownerPlayerId", context.ownerPlayerId());
        content.put("memory", selected);
        content.put("sourceSequences", sources);
        content.put("authorityBoundary", "Memory contains private cognition and hypotheses, not authoritative game facts.");
        return ToolResult.success(call, content, sources);
    }

    private List<Long> collectSources(Map<String, Object> memory) {
        List<Map<String, Object>> sourced = memory.values().stream()
                .flatMap(value -> StrategicToolSupport.maps(value).stream()).toList();
        java.util.Set<Long> result = new java.util.LinkedHashSet<>(StrategicToolSupport.sequences(sourced));
        Object bindings = memory.get("beliefEvidenceReferences");
        if (bindings instanceof Map<?, ?> values) {
            values.values().forEach(value -> result.addAll(StrategicToolSupport.numbers(value)));
        }
        return List.copyOf(result);
    }
}
