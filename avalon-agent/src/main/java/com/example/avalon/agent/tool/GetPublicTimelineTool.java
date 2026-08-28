package com.example.avalon.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public final class GetPublicTimelineTool implements AgentTool {
    private static final ToolDescriptor DESCRIPTOR = StrategicToolSupport.descriptor(
            "get_public_timeline",
            "Query public events already visible to this agent. Every event retains its sequence source.",
            Map.of(
                    "roundNo", Map.of("type", "integer", "minimum", 1),
                    "playerId", Map.of("type", "string"),
                    "eventTypes", Map.of("type", "array", "items", Map.of("type", "string"))));

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, ToolCall call) {
        StrategicToolSupport.rejectUnknownArguments(call.arguments(), "roundNo", "playerId", "eventTypes");
        Integer roundNo = StrategicToolSupport.optionalInteger(call.arguments(), "roundNo");
        String playerId = StrategicToolSupport.optionalString(call.arguments(), "playerId");
        Set<String> eventTypes = Set.copyOf(StrategicToolSupport.optionalStrings(call.arguments(), "eventTypes"));
        List<Map<String, Object>> events = visibleEvents(context).stream()
                .filter(event -> roundNo == null || roundNo(event) == roundNo)
                .filter(event -> playerId == null || playerId.equals(event.get("actorPlayerId")))
                .filter(event -> eventTypes.isEmpty() || eventTypes.contains(String.valueOf(event.get("eventType"))))
                .toList();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("fromSequenceExclusive", context.request().getObservationFromSequence());
        content.put("throughSequenceInclusive", context.request().getObservationToSequence());
        content.put("events", events);
        content.put("sourceSequences", StrategicToolSupport.sequences(events));
        return ToolResult.success(call, content, StrategicToolSupport.sequences(events));
    }

    private List<Map<String, Object>> visibleEvents(ToolExecutionContext context) {
        Map<Long, Map<String, Object>> bySequence = new LinkedHashMap<>();
        List<Map<String, Object>> events = new ArrayList<>(StrategicToolSupport.maps(
                context.request().getMemory().get("worldFacts")));
        events.addAll(context.request().getObservationDelta());
        events.stream().filter(event -> sequence(event) > 0).forEach(event -> bySequence.put(sequence(event), event));
        return bySequence.values().stream().sorted(Comparator.comparingLong(this::sequence)).toList();
    }

    private long sequence(Map<String, Object> event) {
        Object value = event.get("sequence");
        if (!(value instanceof Number)) value = event.get("sourceEventSequence");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : -1;
    }

    private int roundNo(Map<String, Object> event) {
        int value = number(event.get("roundNo"));
        return value > 0 ? value : number(StrategicToolSupport.stringMap(event.get("facts")).get("roundNo"));
    }
}
