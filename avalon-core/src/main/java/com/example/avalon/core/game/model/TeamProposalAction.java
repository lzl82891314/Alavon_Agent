package com.example.avalon.core.game.model;

import com.example.avalon.core.game.enums.PlayerActionType;

import java.util.List;

public record TeamProposalAction(List<String> selectedPlayerIds) implements PlayerAction {
    public TeamProposalAction {
        selectedPlayerIds = selectedPlayerIds == null ? List.of() : List.copyOf(selectedPlayerIds);
    }

    @Override
    public PlayerActionType actionType() {
        return PlayerActionType.TEAM_PROPOSAL;
    }
}

