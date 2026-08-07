package com.example.avalon.agent.harness;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.service.AgentTurnRequestFactory;
import com.example.avalon.agent.service.PromptBuilder;
import com.example.avalon.agent.service.ValidatedAgentTurn;
import com.example.avalon.agent.service.ResponseParser;
import com.example.avalon.agent.service.ValidationRetryPolicy;
import com.example.avalon.core.game.model.PlayerTurnContext;

/** Default harness pipeline: host view -> bounded prompt -> model -> structured validation -> retry. */
public final class DefaultAgentHarness implements AgentHarness {
    private final AgentTurnLifecycle lifecycle;

    public DefaultAgentHarness(AgentTurnRequestFactory requests, PromptBuilder prompts, AgentGateway gateway,
                               ResponseParser parser, ValidationRetryPolicy retry) {
        this.lifecycle = new DefaultAgentTurnLifecycle(requests, prompts, gateway, parser, retry);
    }

    @Override public HarnessExecution execute(PlayerTurnContext context, PlayerAgentConfig profile) {
        return lifecycle.execute(lifecycle.prepare(context, profile));
    }
}
