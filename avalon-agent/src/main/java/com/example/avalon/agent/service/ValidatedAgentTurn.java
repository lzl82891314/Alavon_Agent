package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.core.game.model.PlayerAction;

public record ValidatedAgentTurn(
        AgentTurnResult turnResult,
        PlayerAction action,
        int attempts,
        AgentTurnRequest request
) {
}
