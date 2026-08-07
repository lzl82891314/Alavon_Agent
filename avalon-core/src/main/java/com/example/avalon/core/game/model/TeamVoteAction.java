package com.example.avalon.core.game.model;

import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.enums.VoteChoice;

public record TeamVoteAction(VoteChoice vote) implements PlayerAction {
    @Override
    public PlayerActionType actionType() {
        return PlayerActionType.TEAM_VOTE;
    }
}

