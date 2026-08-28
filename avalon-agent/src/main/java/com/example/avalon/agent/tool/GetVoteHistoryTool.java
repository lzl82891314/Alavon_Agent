package com.example.avalon.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class GetVoteHistoryTool implements AgentTool {
    private static final ToolDescriptor DESCRIPTOR = StrategicToolSupport.descriptor(
            "get_vote_history",
            "Query visible team proposals, public vote evidence, and mission outcomes without identity inference.",
            Map.of("roundNo", Map.of("type", "integer", "minimum", 1), "playerId", Map.of("type", "string")));

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, ToolCall call) {
        StrategicToolSupport.rejectUnknownArguments(call.arguments(), "roundNo", "playerId");
        Integer roundNo = StrategicToolSupport.optionalInteger(call.arguments(), "roundNo");
        String playerId = StrategicToolSupport.optionalString(call.arguments(), "playerId");
        List<Map<String, Object>> votes = filter(
                StrategicToolSupport.maps(context.request().getStrategyContext().get("voteEvidence")), roundNo, playerId);
        List<Map<String, Object>> teams = filter(
                StrategicToolSupport.maps(context.request().getStrategyContext().get("teamHistory")), roundNo, playerId);
        List<Map<String, Object>> missions = filter(
                StrategicToolSupport.maps(context.request().getStrategyContext().get("missionConstraints")), roundNo, playerId);
        List<Map<String, Object>> all = java.util.stream.Stream.of(votes, teams, missions).flatMap(List::stream).toList();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("voteEvidence", votes);
        content.put("teamHistory", teams);
        content.put("missionConstraints", missions);
        content.put("sourceSequences", StrategicToolSupport.sequences(all));
        return ToolResult.success(call, content, StrategicToolSupport.sequences(all));
    }

    private List<Map<String, Object>> filter(List<Map<String, Object>> values, Integer roundNo, String playerId) {
        return values.stream()
                .filter(item -> roundNo == null || !(item.get("roundNo") instanceof Number number)
                        || number.intValue() == roundNo)
                .filter(item -> playerId == null || mentionsPlayer(item, playerId))
                .toList();
    }

    private boolean mentionsPlayer(Map<String, Object> item, String playerId) {
        return item.values().stream().anyMatch(value -> playerId.equals(value)
                || StrategicToolSupport.strings(value).contains(playerId));
    }
}
