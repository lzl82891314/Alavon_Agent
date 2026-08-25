package com.example.avalon.agent.gateway;

public record ModelStreamEvent(
        String callId,
        String gameId,
        String playerId,
        String phase,
        String modelId,
        ModelStreamEventType type,
        String delta,
        long elapsedMillis,
        Integer transportAttempts
) {
}
