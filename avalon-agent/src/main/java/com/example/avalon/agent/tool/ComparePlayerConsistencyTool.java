package com.example.avalon.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class ComparePlayerConsistencyTool implements AgentTool {
    private static final ToolDescriptor DESCRIPTOR = StrategicToolSupport.descriptor(
            "compare_player_consistency",
            "Compare one player's visible speech, commitments, proposals, and votes using sourced public evidence.",
            Map.of("playerId", Map.of("type", "string")), "playerId");

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, ToolCall call) {
        StrategicToolSupport.rejectUnknownArguments(call.arguments(), "playerId");
        String playerId = StrategicToolSupport.optionalString(call.arguments(), "playerId");
        if (playerId == null) throw new IllegalArgumentException("playerId is required");
        requireKnownPlayer(context, playerId);
        List<Map<String, Object>> events = context.request().getObservationDelta().stream()
                .filter(event -> playerId.equals(event.get("actorPlayerId"))).toList();
        List<Map<String, Object>> contradictions = StrategicToolSupport.maps(
                        context.request().getStrategyContext().get("contradictionCandidates")).stream()
                .filter(item -> mentionsPlayer(item, playerId)).toList();
        List<Map<String, Object>> all = java.util.stream.Stream.concat(events.stream(), contradictions.stream()).toList();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("playerId", playerId);
        content.put("visibleEvents", events);
        content.put("contradictionCandidates", contradictions);
        content.put("interpretationBoundary", "Candidates are public evidence, not an authoritative role conclusion.");
        content.put("sourceSequences", StrategicToolSupport.sequences(all));
        return ToolResult.success(call, content, StrategicToolSupport.sequences(all));
    }

    private void requireKnownPlayer(ToolExecutionContext context, String playerId) {
        boolean known = StrategicToolSupport.maps(context.request().getPublicState().get("players")).stream()
                .anyMatch(player -> playerId.equals(player.get("playerId")));
        if (!known) throw new IllegalArgumentException("Unknown public player id: " + playerId);
    }

    private boolean mentionsPlayer(Map<String, Object> item, String playerId) {
        return item.values().stream().anyMatch(value -> playerId.equals(value)
                || StrategicToolSupport.strings(value).contains(playerId));
    }
}
