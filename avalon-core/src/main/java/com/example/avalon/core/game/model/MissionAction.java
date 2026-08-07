package com.example.avalon.core.game.model;

import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.PlayerActionType;

public record MissionAction(MissionChoice choice) implements PlayerAction {
    @Override
    public PlayerActionType actionType() {
        return PlayerActionType.MISSION_ACTION;
    }
}

