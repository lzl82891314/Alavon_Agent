package com.example.avalon.runtime.model;

import com.example.avalon.core.game.enums.GamePhase;

import java.time.Instant;
import java.util.Map;

public record GameEvent(
        long seqNo,
        String type,
        GamePhase phase,
        String actorId,
        Map<String, Object> payload,
        Instant createdAt) {
}
