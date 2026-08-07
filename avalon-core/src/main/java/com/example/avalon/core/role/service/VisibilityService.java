package com.example.avalon.core.role.service;

import com.example.avalon.core.game.model.GameRuleContext;
import com.example.avalon.core.player.memory.PlayerPrivateView;

public interface VisibilityService {
    PlayerPrivateView buildPrivateView(GameRuleContext context, String playerId);
}

