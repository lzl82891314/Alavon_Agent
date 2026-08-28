package com.example.avalon.agent.model;

import com.example.avalon.agent.tool.ToolCall;

import java.util.List;

/** Protocol-neutral intermediate result: either tool calls or one final structured output. */
public record AgentModelTurn(List<ToolCall> toolCalls, AgentTurnResult finalResult,
                             RawCompletionMetadata modelMetadata) {
    public AgentModelTurn {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        if (toolCalls.isEmpty() == (finalResult == null)) {
            throw new IllegalArgumentException("Model turn must contain either tool calls or a final result");
        }
        modelMetadata = modelMetadata == null ? new RawCompletionMetadata() : modelMetadata;
    }

    public static AgentModelTurn tools(List<ToolCall> calls, RawCompletionMetadata metadata) {
        return new AgentModelTurn(calls, null, metadata);
    }

    public static AgentModelTurn completed(AgentTurnResult result) {
        return new AgentModelTurn(List.of(), result, result == null ? null : result.getModelMetadata());
    }

    public boolean requiresTools() {
        return !toolCalls.isEmpty();
    }
}
