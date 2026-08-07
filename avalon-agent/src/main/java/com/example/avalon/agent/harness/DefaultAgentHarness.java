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
    private final AgentTurnRequestFactory requests;
    private final PromptBuilder prompts;
    private final AgentGateway gateway;
    private final ResponseParser parser;
    private final ValidationRetryPolicy retry;

    public DefaultAgentHarness(AgentTurnRequestFactory requests, PromptBuilder prompts, AgentGateway gateway,
                               ResponseParser parser, ValidationRetryPolicy retry) {
        this.requests = requests; this.prompts = prompts; this.gateway = gateway; this.parser = parser; this.retry = retry;
    }

    @Override public HarnessExecution execute(PlayerTurnContext context, PlayerAgentConfig profile) {
        AgentTurnRequest request = requests.create(context, profile);
        request.setPromptText(prompts.build(request));
        ValidatedAgentTurn validated = retry.execute(context, request, gateway, parser);
        return new HarnessExecution(validated.request(), validated.turnResult(), validated.action(), validated.attempts());
    }
}
