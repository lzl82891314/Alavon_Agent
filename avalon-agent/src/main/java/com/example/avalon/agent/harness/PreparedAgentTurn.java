package com.example.avalon.agent.harness;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.core.game.model.PlayerTurnContext;

/** Immutable input produced before a model call; execution must not rebuild game state. */
public record PreparedAgentTurn(PlayerTurnContext context, PlayerAgentConfig profile, AgentTurnRequest request) { }
