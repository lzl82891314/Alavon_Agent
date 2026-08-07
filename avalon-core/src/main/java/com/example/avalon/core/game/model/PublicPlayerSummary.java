package com.example.avalon.core.game.model;

import com.example.avalon.core.game.enums.PlayerConnectionState;
import com.example.avalon.core.player.enums.PlayerControllerType;

public record PublicPlayerSummary(
        String gameId,
        String playerId,
        Integer seatNo,
        String displayName,
        PlayerControllerType controllerType,
        PlayerConnectionState connectionState
) {
}

