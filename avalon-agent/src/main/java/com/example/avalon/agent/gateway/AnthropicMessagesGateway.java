package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AgentLoopStep;
import com.example.avalon.agent.model.AgentModelTurn;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.example.avalon.agent.tool.ToolCall;
import com.example.avalon.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native adapter for Anthropic's Messages API. */
@Component
public final class AnthropicMessagesGateway implements ModelProtocolAdapter {
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";
    private static final String DEFAULT_VERSION = "2023-06-01";
    private final OpenAiHttpTransport transport;
    private final ModelProfileApiKeyResolver apiKeys;
    private final ModelStreamEventPublisher streamEvents;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public AnthropicMessagesGateway(OpenAiHttpTransport transport, ModelProfileApiKeyResolver apiKeys) {
        this(transport, apiKeys, ModelStreamEventPublisher.noop());
    }

    @Autowired
    public AnthropicMessagesGateway(OpenAiHttpTransport transport,
                                    ModelProfileApiKeyResolver apiKeys,
                                    ModelStreamEventPublisher streamEvents) {
        this.transport = transport;
        this.apiKeys = apiKeys;
        this.streamEvents = streamEvents;
    }

    @Override
    public String protocolId() {
        return "ANTHROPIC_MESSAGES";
    }

    @Override
    public AgentTurnResult playTurn(AgentTurnRequest request) {
        AgentModelTurn turn = nextTurn(request);
        if (turn.requiresTools()) throw new IllegalStateException("Default harness cannot execute model tool calls");
        return turn.finalResult();
    }

    @Override
    public AgentModelTurn nextTurn(AgentTurnRequest request) {
        Map<String, Object> options = request.getProviderOptions();
        String apiKey = apiKeys.resolveApiKey(request.getModelId(), options);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(ModelProfileSecretSupport.missingApiKeyMessage("anthropic", request.getModelId()));
        }
        String baseUrl = OpenAiCompatibleSupport.stringOption(options, "baseUrl");
        Duration timeout = OpenAiCompatibleSupport.effectiveTimeout(request.getProvider(), options.get("timeoutMillis"));
        long startedAt = System.nanoTime();
        String callId = streamEvents.started(request);
        AnthropicSseAccumulator accumulator = new AnthropicSseAccumulator(
                json, request, streamEvents, callId, startedAt);
        SseHttpResponse streamResponse;
        JsonNode response;
        try {
            streamResponse = transport.postEventStream(
                    OpenAiCompatibleSupport.anthropicMessagesEndpointUri(
                            baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl),
                    headers(apiKey, options),
                    requestBody(request, requiredMaxOutputTokens(options, request.getModelId())),
                    timeout,
                    accumulator::accept
            );
        } catch (OpenAiCompatibleTransportException exception) {
            streamEvents.failed(callId, request, startedAt);
            throw OpenAiCompatibleSupport.transportResponseException(
                    request, exception, "anthropic", request.getModelName());
        } catch (RuntimeException exception) {
            streamEvents.failed(callId, request, startedAt);
            throw OpenAiCompatibleSupport.protocolResponseException(
                    request, exception, "anthropic", request.getModelName(), "invalid_stream");
        }
        try {
            response = accumulator.response();
        } catch (RuntimeException exception) {
            streamEvents.failed(callId, request, startedAt);
            throw OpenAiCompatibleSupport.protocolResponseException(
                    request, exception, "anthropic", request.getModelName(), "stream_interrupted");
        }
        AgentModelTurn turn;
        try {
            turn = parseModelTurn(request, response);
            Map<String, Object> attributes = turn.modelMetadata().getAttributes();
            attributes.put("streaming", true);
            attributes.put("transportAttempts", streamResponse.transportAttempts());
            attributes.put("reasoningChars", accumulator.reasoningChars());
            putIfPresent(attributes, "reasoningDetails", accumulator.reasoning());
            putIfPresent(attributes, "reasoningDetailsPreview", OpenAiCompatibleSupport.contentPreview(accumulator.reasoning()));
            putIfPresent(attributes, "firstReasoningDeltaMs", accumulator.firstReasoningDeltaMs());
            putIfPresent(attributes, "firstContentDeltaMs", accumulator.firstContentDeltaMs());
        } catch (RuntimeException exception) {
            streamEvents.failed(callId, request, startedAt);
            throw OpenAiCompatibleSupport.protocolResponseException(
                    request, exception, "anthropic", request.getModelName(), "invalid_response");
        }
        streamEvents.completed(callId, request, startedAt, streamResponse.transportAttempts());
        return turn;
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
        root.put("stream", true);
        root.put("system", "你负责控制一名阿瓦隆玩家。公开发言和 privateThought 必须使用简体中文；只返回一个 JSON 对象，不要输出原始思维链。");
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "user").put("content", request.getPromptText());
        appendLoopMessages(messages, request);
        if (!request.getTools().isEmpty()) ToolCallingJsonSupport.addAnthropicTools(json, root, request.getTools());
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
            RawCompletionMetadata metadata = metadata(request, response);
            OpenAiCompatibleSupport.parseOptionalSections(json, payload, result, metadata);
            result.setModelMetadata(metadata);
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Anthropic Messages assistant content was not valid JSON", exception);
        }
    }

    private AgentModelTurn parseModelTurn(AgentTurnRequest request, JsonNode response) {
        List<ToolCall> calls = new java.util.ArrayList<>();
        for (JsonNode block : response.path("content")) {
            if (!"tool_use".equals(block.path("type").asText())) continue;
            calls.add(new ToolCall(block.path("id").asText(), block.path("name").asText(),
                    ToolCallingJsonSupport.arguments(json, block.path("input"))));
        }
        if (calls.isEmpty()) return AgentModelTurn.completed(parseResponse(request, response));
        return AgentModelTurn.tools(calls, metadata(request, response));
    }

    private void appendLoopMessages(ArrayNode messages, AgentTurnRequest request) {
        for (AgentLoopStep step : request.getLoopSteps()) {
            ArrayNode assistantContent = messages.addObject().put("role", "assistant").putArray("content");
            for (ToolCall call : step.toolCalls()) {
                ObjectNode block = assistantContent.addObject();
                block.put("type", "tool_use");
                block.put("id", call.callId());
                block.put("name", call.toolName());
                block.set("input", json.valueToTree(call.arguments()));
            }
            ArrayNode userContent = messages.addObject().put("role", "user").putArray("content");
            for (ToolResult result : step.toolResults()) {
                userContent.addObject().put("type", "tool_result").put("tool_use_id", result.callId())
                        .put("content", ToolCallingJsonSupport.resultJson(json, result));
            }
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

    private void putIfPresent(Map<String, Object> values, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            values.put(key, value);
        }
    }
}
