package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Primary
@Component
public class RoutingAgentGateway implements AgentGateway {
    private static final List<String> OPENAI_COMPATIBLE_PROVIDER_IDS = List.of(
            "openai",
            "minimax",
            "glm",
            "claude",
            "qwen"
    );
    private static final Set<String> OPENAI_COMPATIBLE_PROVIDERS = Set.copyOf(OPENAI_COMPATIBLE_PROVIDER_IDS);

    private final AgentGateway noopGateway;
    private final AgentGateway openAiGateway;
    private final AgentGateway openAiResponsesGateway;

    @Autowired
    public RoutingAgentGateway(NoopAgentGateway noopGateway, OpenAiChatCompletionsGateway openAiGateway,
                               OpenAiResponsesGateway openAiResponsesGateway) {
        this((AgentGateway) noopGateway, openAiGateway, openAiResponsesGateway);
    }

    RoutingAgentGateway(AgentGateway noopGateway, AgentGateway openAiGateway) {
        this(noopGateway, openAiGateway, openAiGateway);
    }

    RoutingAgentGateway(AgentGateway noopGateway, AgentGateway openAiGateway, AgentGateway openAiResponsesGateway) {
        this.noopGateway = noopGateway;
        this.openAiGateway = openAiGateway;
        this.openAiResponsesGateway = openAiResponsesGateway;
    }

    @Override
    public AgentTurnResult playTurn(AgentTurnRequest request) {
        String provider = normalizedProvider(request.getProvider());
        if (provider == null || "noop".equals(provider)) {
            return noopGateway.playTurn(request);
        }
        if (OPENAI_COMPATIBLE_PROVIDERS.contains(provider)) {
            String protocol = OpenAiCompatibleSupport.stringOption(request.getProviderOptions(), "protocol");
            return "responses".equalsIgnoreCase(protocol) && "openai".equals(provider)
                    ? openAiResponsesGateway.playTurn(request)
                    : openAiGateway.playTurn(request);
        }
        throw new IllegalStateException(
                "Unsupported LLM provider '" + request.getProvider() + "'. Supported providers: "
                        + String.join(", ", OPENAI_COMPATIBLE_PROVIDER_IDS)
                        + ", noop"
        );
    }

    private String normalizedProvider(String provider) {
        if (provider == null) {
            return null;
        }
        String normalized = provider.strip().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
