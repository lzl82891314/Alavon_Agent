package com.example.avalon.agent.harness;

import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.core.game.model.PlayerTurnContext;

public interface AgentTurnLifecycle {
    PreparedAgentTurn prepare(PlayerTurnContext context, PlayerAgentConfig profile);
    HarnessExecution execute(PreparedAgentTurn prepared);
}
