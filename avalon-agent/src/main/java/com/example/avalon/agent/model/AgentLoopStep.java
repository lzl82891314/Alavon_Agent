package com.example.avalon.agent.model;

import com.example.avalon.agent.tool.ToolCall;
import com.example.avalon.agent.tool.ToolResult;

import java.util.List;

/** One assistant tool request and the host results returned to the next model invocation. */
public record AgentLoopStep(List<ToolCall> toolCalls, List<ToolResult> toolResults) {
    public AgentLoopStep {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        if (toolCalls.isEmpty() || toolCalls.size() != toolResults.size()) {
            throw new IllegalArgumentException("Each loop step must pair tool calls with results");
        }
    }
}
