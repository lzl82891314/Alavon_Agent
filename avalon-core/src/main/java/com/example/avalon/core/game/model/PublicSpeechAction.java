package com.example.avalon.core.game.model;

import com.example.avalon.core.game.enums.PlayerActionType;

public record PublicSpeechAction(String speechText) implements PlayerAction {
    @Override
    public PlayerActionType actionType() {
        return PlayerActionType.PUBLIC_SPEECH;
    }
}

