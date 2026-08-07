package com.example.avalon.core.game.model;

import com.example.avalon.core.player.memory.AuditReason;
import com.example.avalon.core.player.memory.MemoryUpdate;

import java.util.Map;

public record PlayerActionResult(
        String publicSpeech,
        PlayerAction action,
        AuditReason auditReason,
        MemoryUpdate memoryUpdate,
        Map<String, Object> rawMetadata
) {
    public PlayerActionResult {
        rawMetadata = rawMetadata == null ? Map.of() : Map.copyOf(rawMetadata);
    }
}

