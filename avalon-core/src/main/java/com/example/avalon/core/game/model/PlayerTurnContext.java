package com.example.avalon.core.game.model;

import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;

public record PlayerTurnContext(
        String gameId,
        Integer roundNo,
        String phase,
        String playerId,
        Integer seatNo,
        String roleId,
        PublicGameSnapshot publicState,
        PlayerPrivateView privateView,
        PlayerMemoryState memoryState,
        AllowedActionSet allowedActions,
        RuleSetDefinition ruleSetDefinition,
        SetupTemplate setupTemplate,
        String rulesSummary
) {
}

