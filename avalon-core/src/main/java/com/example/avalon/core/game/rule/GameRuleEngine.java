package com.example.avalon.core.game.rule;

import com.example.avalon.core.game.model.AllowedActionSet;
import com.example.avalon.core.game.model.GameRuleContext;
import com.example.avalon.core.game.model.GameSession;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.enums.GamePhase;

public interface GameRuleEngine {
    AllowedActionSet allowedActions(GameRuleContext context, String playerId);

    void validateAction(GameRuleContext context, String actorPlayerId, PlayerAction action);

    GameSession applyAction(GameRuleContext context, String actorPlayerId, PlayerAction action);

    GamePhase nextPhase(GameRuleContext context);

    boolean isGameEnded(GameRuleContext context);
}
