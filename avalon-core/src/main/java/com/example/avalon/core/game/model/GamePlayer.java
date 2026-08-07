package com.example.avalon.core.game.model;

import com.example.avalon.core.game.enums.PlayerConnectionState;
import com.example.avalon.core.player.enums.PlayerControllerType;

public record GamePlayer(
        String gameId,
        String playerId,
        Integer seatNo,
        String displayName,
        PlayerControllerType controllerType,
        String controllerConfigJson,
        PlayerConnectionState connectionState
) {
    public GamePlayer {
        connectionState = connectionState == null ? PlayerConnectionState.DISCONNECTED : connectionState;
    }
}

