package com.example.avalon.core.player.memory;

import com.example.avalon.core.game.enums.Camp;

import java.util.List;

public record VisiblePlayerInfo(
        String playerId,
        Integer seatNo,
        String displayName,
        String exactRoleId,
        Camp camp,
        List<String> candidateRoleIds
) {
    public VisiblePlayerInfo {
        candidateRoleIds = candidateRoleIds == null ? List.of() : List.copyOf(candidateRoleIds);
    }
}

