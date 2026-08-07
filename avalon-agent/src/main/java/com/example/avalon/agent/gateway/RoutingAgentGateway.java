package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Primary
@Component
public class RoutingAgentGateway implements AgentGateway {
    private final AgentGateway noopGateway;
    private final ModelProtocolAdapterRegistry protocolAdapters;

    @Autowired
    public RoutingAgentGateway(NoopAgentGateway noopGateway, ModelProtocolAdapterRegistry protocolAdapters) {
        this.noopGateway = noopGateway;
        this.protocolAdapters = protocolAdapters;
    }

    @Override
    public AgentTurnResult playTurn(AgentTurnRequest request) {
        String provider = normalizedProvider(request.getProvider());
        if (provider == null || "noop".equals(provider)) {
            return noopGateway.playTurn(request);
        }
        return protocolAdapters.require(request.getProtocol()).playTurn(request);
    }

    private String normalizedProvider(String provider) {
        if (provider == null) {
            return null;
        }
        String normalized = provider.strip().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
