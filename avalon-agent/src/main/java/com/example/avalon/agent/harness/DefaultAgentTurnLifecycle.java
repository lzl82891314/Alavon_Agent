package com.example.avalon.agent.harness;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.service.AgentTurnRequestFactory;
import com.example.avalon.agent.service.PromptBuilder;
import com.example.avalon.agent.service.ResponseParser;
import com.example.avalon.agent.service.ValidatedAgentTurn;
import com.example.avalon.agent.service.ValidationRetryPolicy;
import com.example.avalon.core.game.model.PlayerActionResult;
import com.example.avalon.core.game.model.PlayerTurnContext;

public final class DefaultAgentTurnLifecycle implements AgentTurnLifecycle {
    private final AgentTurnRequestFactory requests;
    private final PromptBuilder prompts;
    private final AgentGateway gateway;
    private final ResponseParser parser;
    private final ValidationRetryPolicy retry;

    public DefaultAgentTurnLifecycle(AgentTurnRequestFactory requests, PromptBuilder prompts, AgentGateway gateway,
                                     ResponseParser parser, ValidationRetryPolicy retry) {
        this.requests = requests; this.prompts = prompts; this.gateway = gateway; this.parser = parser; this.retry = retry;
    }

    @Override public PreparedAgentTurn prepare(PlayerTurnContext context, PlayerAgentConfig profile) {
        AgentTurnRequest request = requests.create(context, profile);
        request.setPromptText(prompts.build(request));
        return new PreparedAgentTurn(context, profile, request);
    }

    @Override public HarnessExecution execute(PreparedAgentTurn prepared) {
        ValidatedAgentTurn validated = retry.execute(prepared.context(), prepared.request(), gateway, parser);
        return new HarnessExecution(validated.request(), validated.turnResult(), validated.action(), validated.attempts());
    }

    @Override public void commitAccepted(PreparedAgentTurn prepared, PlayerActionResult result, long acceptedGameVersion) { }
}
