package com.example.avalon.agent.harness;

import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.core.game.model.PlayerTurnContext;

public interface AgentHarness {
    HarnessExecution execute(PlayerTurnContext context, PlayerAgentConfig profile);
}
