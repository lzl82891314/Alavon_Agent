package com.example.avalon.core.role.model;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;

import java.time.Instant;

public record RoleAssignment(
        String gameId,
        String playerId,
        Integer seatNo,
        String roleId,
        Camp camp,
        PlayerPrivateKnowledge privateKnowledge,
        Instant assignedAt
) {
}

