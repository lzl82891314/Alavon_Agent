package com.example.avalon.core.player.controller;

import com.example.avalon.core.game.model.PlayerActionResult;
import com.example.avalon.core.game.model.PlayerTurnContext;

public interface PlayerController {
    PlayerActionResult act(PlayerTurnContext context);
}

