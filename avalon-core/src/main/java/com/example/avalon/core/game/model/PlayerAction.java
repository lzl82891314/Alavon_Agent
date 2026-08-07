package com.example.avalon.core.game.model;

import com.example.avalon.core.game.enums.PlayerActionType;

public sealed interface PlayerAction permits PublicSpeechAction, TeamProposalAction, TeamVoteAction, MissionAction, AssassinationAction {
    PlayerActionType actionType();

    default String type() {
        return actionType().name();
    }
}

