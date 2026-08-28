package com.example.avalon.agent.tool;

import com.example.avalon.agent.model.AgentTurnRequest;

import java.time.Instant;
import java.util.Objects;

public record ToolExecutionContext(
        String agentRunId,
        String gameId,
        String ownerPlayerId,
        String agentInstanceId,
        AgentTurnRequest request,
        Instant deadline
) {
    public ToolExecutionContext {
        if (agentRunId == null || agentRunId.isBlank()) throw new IllegalArgumentException("Agent run id is required");
        if (request == null) throw new IllegalArgumentException("Agent turn request is required");
        if (!Objects.equals(request.getGameId(), gameId) || !Objects.equals(request.getPlayerId(), ownerPlayerId)) {
            throw new IllegalArgumentException("Tool identity must match the host-provided turn request");
        }
        Object expectedAgent = request.getMemory().get("agentInstanceId");
        if (expectedAgent != null && !Objects.equals(String.valueOf(expectedAgent), agentInstanceId)) {
            throw new IllegalArgumentException("Tool agent identity must match the host-projected memory");
        }
        deadline = deadline == null ? Instant.MAX : deadline;
    }
}
