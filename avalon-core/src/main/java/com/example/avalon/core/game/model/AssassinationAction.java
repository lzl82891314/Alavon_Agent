package com.example.avalon.core.game.model;

import com.example.avalon.core.game.enums.PlayerActionType;

public record AssassinationAction(String targetPlayerId) implements PlayerAction {
    @Override
    public PlayerActionType actionType() {
        return PlayerActionType.ASSASSINATION;
    }
}

