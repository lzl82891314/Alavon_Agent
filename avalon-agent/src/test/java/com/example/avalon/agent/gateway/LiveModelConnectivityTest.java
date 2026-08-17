package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LiveModelConnectivityTest {
    @Test
    void configuredOpenAiCompatibleModelReturnsAResponse() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("AVALON_LIVE_MODEL_TEST")),
                "Set AVALON_LIVE_MODEL_TEST=true to run the live model check");

        String modelName = optionalEnv("AVALON_LIVE_MODEL_NAME", "deepseek-v4-flash");
        String baseUrl = optionalEnv("AVALON_LIVE_MODEL_BASE_URL", "https://gcapi.cn/v1");
        String apiKey = optionalEnv("AVALON_LIVE_MODEL_API_KEY", null);
        if (apiKey == null) {
            apiKey = requiredEnv("DEEPSEEK_API_KEY");
        }

        AgentTurnRequest request = new AgentTurnRequest();
        request.setModelId(modelName);
        request.setModelName(modelName);
        request.setProvider("deepseek");
        request.setProtocol("OPENAI_COMPATIBLE_CHAT");
        request.setProviderOptions(Map.of(
                "baseUrl", baseUrl,
                "apiKey", apiKey,
                "instructionRole", "system"
        ));
        request.setAllowedActions(List.of("PUBLIC_SPEECH"));
        request.setPromptText("Return one JSON object with an action field: {\"action\":{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"ok\"}}.");

        AgentTurnResult result = new OpenAiChatCompletionsGateway(
                new JdkOpenAiHttpTransport(),
                ignored -> null
        ).playTurn(request);

        assertFalse(result.getActionJson() == null || result.getActionJson().isBlank());
    }

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        assumeTrue(value != null && !value.isBlank(), "Set " + name + " to run the live model check");
        return value;
    }

    private String optionalEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
