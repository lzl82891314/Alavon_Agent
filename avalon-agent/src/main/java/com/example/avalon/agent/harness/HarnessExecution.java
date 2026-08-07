package com.example.avalon.agent.harness;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.core.game.model.PlayerAction;

public record HarnessExecution(AgentTurnRequest request, AgentTurnResult turnResult,
                               PlayerAction action, int attempts) {}
