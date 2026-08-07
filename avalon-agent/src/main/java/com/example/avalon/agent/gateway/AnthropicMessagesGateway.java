package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native adapter for Anthropic's Messages API. */
@Component
public final class AnthropicMessagesGateway implements ModelProtocolAdapter {
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";
    private static final String DEFAULT_VERSION = "2023-06-01";
    private final OpenAiHttpTransport transport;
    private final ModelProfileApiKeyResolver apiKeys;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public AnthropicMessagesGateway(OpenAiHttpTransport transport, ModelProfileApiKeyResolver apiKeys) {
        this.transport = transport;
        this.apiKeys = apiKeys;
    }

    @Override
    public String protocolId() {
        return "ANTHROPIC_MESSAGES";
    }

    @Override
    public AgentTurnResult playTurn(AgentTurnRequest request) {
        Map<String, Object> options = request.getProviderOptions();
        String apiKey = apiKeys.resolveApiKey(request.getModelId(), options);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(ModelProfileSecretSupport.missingApiKeyMessage("anthropic", request.getModelId()));
        }
        String baseUrl = OpenAiCompatibleSupport.stringOption(options, "baseUrl");
        Duration timeout = OpenAiCompatibleSupport.effectiveTimeout(request.getProvider(), options.get("timeoutMillis"));
        JsonNode response = transport.postChatCompletion(
                OpenAiCompatibleSupport.anthropicMessagesEndpointUri(baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl),
                headers(apiKey, options),
                requestBody(request, requiredMaxOutputTokens(options, request.getModelId())),
                timeout
        );
        return parseResponse(request, response);
    }

    private Map<String, String> headers(String apiKey, Map<String, Object> options) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", optionOrDefault(options, "anthropicVersion", DEFAULT_VERSION));
        return headers;
    }

    private String requestBody(AgentTurnRequest request, int maxOutputTokens) {
        ObjectNode root = json.createObjectNode();
        root.put("model", requiredModelName(request));
        root.put("max_tokens", maxOutputTokens);
        root.put("system", "You control an Avalon player. Return exactly one JSON object. Do not reveal chain-of-thought.");
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "user").put("content", request.getPromptText());
        if (request.getTemperature() != null) {
            root.put("temperature", request.getTemperature());
        }
        try {
            return json.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize Anthropic Messages request", exception);
        }
    }

    private AgentTurnResult parseResponse(AgentTurnRequest request, JsonNode response) {
        String output = textContent(response.path("content"));
        if (output == null || output.isBlank()) {
            throw new IllegalStateException("Anthropic Messages response did not include text content");
        }
        try {
            JsonNode payload = json.readTree(output);
            JsonNode action = payload.path("action");
            if (action.isMissingNode() || action.isNull()) {
                throw new IllegalStateException("Anthropic Messages response did not include an action object");
            }
            AgentTurnResult result = new AgentTurnResult();
            result.setPublicSpeech(textOrNull(payload.path("publicSpeech")));
            result.setPrivateThought(textOrNull(payload.path("privateThought")));
            result.setActionJson(action.isTextual() ? action.asText() : json.writeValueAsString(action));
            result.setModelMetadata(metadata(request, response));
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Anthropic Messages response did not contain valid action JSON", exception);
        }
    }

    private RawCompletionMetadata metadata(AgentTurnRequest request, JsonNode response) {
        RawCompletionMetadata metadata = new RawCompletionMetadata();
        metadata.setProvider(request.getProvider());
        metadata.setModelName(textOrNull(response.path("model")) == null ? request.getModelName() : textOrNull(response.path("model")));
        metadata.setInputTokens(longOrNull(response.path("usage").path("input_tokens")));
        metadata.setOutputTokens(longOrNull(response.path("usage").path("output_tokens")));
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("gatewayType", "anthropic-messages");
        putIfPresent(attributes, "messageId", textOrNull(response.path("id")));
        putIfPresent(attributes, "stopReason", textOrNull(response.path("stop_reason")));
        metadata.setAttributes(attributes);
        return metadata;
    }

    private int requiredMaxOutputTokens(Map<String, Object> options, String modelId) {
        Object value = options == null ? null : options.get("maxOutputTokens");
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                int parsed = Integer.parseInt(text.trim());
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Report the same configuration error below.
            }
        }
        throw new IllegalArgumentException("ANTHROPIC_MESSAGES model profile " + modelId
                + " must define providerOptions.maxOutputTokens because Anthropic requires max_tokens");
    }

    private String textContent(JsonNode content) {
        StringBuilder text = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        return text.isEmpty() ? null : text.toString();
    }

    private String requiredModelName(AgentTurnRequest request) {
        if (request.getModelName() == null || request.getModelName().isBlank()) {
            throw new IllegalArgumentException("ANTHROPIC_MESSAGES model profile " + request.getModelId() + " must define modelName");
        }
        return request.getModelName();
    }

    private String optionOrDefault(Map<String, Object> options, String key, String fallback) {
        String value = OpenAiCompatibleSupport.stringOption(options, key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() || node.asText().isBlank() ? null : node.asText();
    }

    private Long longOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asLong();
    }

    private void putIfPresent(Map<String, Object> values, String key, String value) {
        if (value != null) {
            values.put(key, value);
        }
    }
}
