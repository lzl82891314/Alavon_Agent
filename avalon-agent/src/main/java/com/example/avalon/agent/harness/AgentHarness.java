package com.example.avalon.agent.harness;

import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.core.game.model.PlayerTurnContext;

public interface AgentHarness {
    default AgentHarnessType type() {
        return AgentHarnessType.DEFAULT;
    }

    HarnessExecution execute(PlayerTurnContext context, PlayerAgentConfig profile);
}
