package com.example.avalon.core.game.model;

import com.example.avalon.core.game.enums.PlayerActionType;

import java.util.EnumSet;
import java.util.Set;

public record AllowedActionSet(
        String gameId,
        String playerId,
        Integer seatNo,
        Set<PlayerActionType> allowedActionTypes
) {
    public AllowedActionSet {
        allowedActionTypes = allowedActionTypes == null ? EnumSet.noneOf(PlayerActionType.class) : EnumSet.copyOf(allowedActionTypes);
    }

    public boolean allows(PlayerActionType actionType) {
        return allowedActionTypes.contains(actionType);
    }
}

