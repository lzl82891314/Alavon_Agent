package com.example.avalon.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class ListLegalActionsTool implements AgentTool {
    private static final ToolDescriptor DESCRIPTOR = StrategicToolSupport.descriptor(
            "list_legal_actions", "List the host-authorized final game actions and their argument shapes.", Map.of());

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, ToolCall call) {
        StrategicToolSupport.rejectUnknownArguments(call.arguments());
        List<Map<String, Object>> actions = context.request().getAllowedActions().stream()
                .map(type -> Map.<String, Object>of("actionType", type, "schema", schema(type))).toList();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("phase", context.request().getPhase());
        content.put("actions", actions);
        content.put("rule", "A final action is only a proposal until the existing Harness and game rule engine validate it.");
        List<Long> sources = positiveSequence(context.request().getObservationToSequence());
        content.put("sourceSequences", sources);
        return ToolResult.success(call, content, sources);
    }

    private Map<String, Object> schema(String type) {
        return switch (type) {
            case "TEAM_PROPOSAL" -> Map.of("selectedPlayerIds", "string[]");
            case "TEAM_VOTE" -> Map.of("vote", "APPROVE|REJECT");
            case "MISSION_ACTION" -> Map.of("choice", "SUCCESS|FAIL (subject to role rules)");
            case "ASSASSINATION" -> Map.of("targetPlayerId", "string");
            case "PUBLIC_SPEECH" -> Map.of("speechText", "string", "speechAct", "string",
                    "mentions", "string[]", "replyToEventSequences", "long[]");
            default -> Map.of();
        };
    }

    private List<Long> positiveSequence(long sequence) {
        return sequence > 0 ? List.of(sequence) : List.of();
    }
}
