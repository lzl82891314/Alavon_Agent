package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adapter for the OpenAI Responses API; domain code remains dependent only on AgentGateway. */
@Component
public final class OpenAiResponsesGateway implements ModelProtocolAdapter {
    private final OpenAiHttpTransport transport;
    private final ModelProfileApiKeyResolver apiKeys;
    private final ModelStreamEventPublisher streamEvents;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public OpenAiResponsesGateway(OpenAiHttpTransport transport, ModelProfileApiKeyResolver apiKeys) {
        this(transport, apiKeys, ModelStreamEventPublisher.noop());
    }

    @Autowired
    public OpenAiResponsesGateway(OpenAiHttpTransport transport,
                                  ModelProfileApiKeyResolver apiKeys,
                                  ModelStreamEventPublisher streamEvents) {
        this.transport = transport;
        this.apiKeys = apiKeys;
        this.streamEvents = streamEvents;
    }

    @Override
    public String protocolId() {
        return "OPENAI_RESPONSES";
    }

    @Override public AgentTurnResult playTurn(AgentTurnRequest request) {
        Map<String,Object> options = OpenAiCompatibleSupport.effectiveProviderOptions(request.getProvider(), request.getProviderOptions());
        String key = apiKeys.resolveApiKey(request.getModelId(), request.getProviderOptions());
        if (key == null || key.isBlank()) throw new IllegalStateException(ModelProfileSecretSupport.missingApiKeyMessage("openai", request.getModelId()));
        String baseUrl = OpenAiCompatibleSupport.stringOption(options, "baseUrl");
        Duration timeout = OpenAiCompatibleSupport.effectiveTimeout(request.getProvider(), options.get("timeoutMillis"));
        long startedAt = System.nanoTime();
        String callId = streamEvents.started(request);
        OpenAiResponsesSseAccumulator accumulator = new OpenAiResponsesSseAccumulator(
                json, request, streamEvents, callId, startedAt);
        SseHttpResponse streamResponse;
        JsonNode response;
        try {
            streamResponse = transport.postEventStream(
                    OpenAiCompatibleSupport.responsesEndpointUri(baseUrl),
                    Map.of("Authorization", "Bearer " + key), body(request), timeout, accumulator::accept);
        } catch (OpenAiCompatibleTransportException exception) {
            streamEvents.failed(callId, request, startedAt);
            throw OpenAiCompatibleSupport.transportResponseException(
                    request, exception, "openai", "gpt-5.2");
        } catch (RuntimeException exception) {
            streamEvents.failed(callId, request, startedAt);
            throw OpenAiCompatibleSupport.protocolResponseException(
                    request, exception, "openai", "gpt-5.2", "invalid_stream");
        }
        try {
            response = accumulator.response();
        } catch (RuntimeException exception) {
            streamEvents.failed(callId, request, startedAt);
            throw OpenAiCompatibleSupport.protocolResponseException(
                    request, exception, "openai", "gpt-5.2", "stream_interrupted");
        }
        AgentTurnResult result;
        try {
            result = parse(request, response);
            Map<String, Object> attributes = result.getModelMetadata().getAttributes();
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
                    request, exception, "openai", "gpt-5.2", "invalid_response");
        }
        streamEvents.completed(callId, request, startedAt, streamResponse.transportAttempts());
        return result;
    }

    private String body(AgentTurnRequest r) {
        ObjectNode root = json.createObjectNode();
        root.put("model", r.getModelName() == null || r.getModelName().isBlank() ? "gpt-5.2" : r.getModelName());
        root.put("instructions", "你负责控制一名阿瓦隆玩家。公开发言和 privateThought 必须使用简体中文；只返回一个 JSON 对象，不要输出原始思维链。");
        root.put("input", r.getPromptText());
        root.put("store", false);
        root.put("stream", true);
        ObjectNode text = root.putObject("text");
        ObjectNode format = text.putObject("format");
        format.put("type", "json_object");
        if (r.getTemperature() != null) root.put("temperature", r.getTemperature());
        try { return json.writeValueAsString(root); } catch (Exception e) { throw new IllegalStateException("Cannot serialize Responses request", e); }
    }

    private AgentTurnResult parse(AgentTurnRequest request, JsonNode response) {
        String output = response.path("output_text").asText(null);
        if (output == null || output.isBlank()) {
            for (JsonNode item : response.path("output")) for (JsonNode content : item.path("content")) if ("output_text".equals(content.path("type").asText())) output = content.path("text").asText(null);
        }
        if (output == null || output.isBlank()) throw new IllegalStateException("Responses API did not return output_text");
        try {
            JsonNode payload = json.readTree(output);
            AgentTurnResult result = new AgentTurnResult();
            result.setPublicSpeech(payload.path("publicSpeech").asText(null));
            result.setPrivateThought(payload.path("privateThought").asText(null));
            JsonNode action = payload.path("action");
            if (action.isMissingNode() || action.isNull()) {
                throw new IllegalStateException("Responses API response did not include an action object");
            }
            result.setActionJson(action.isTextual() ? action.asText() : json.writeValueAsString(action));
            RawCompletionMetadata metadata = new RawCompletionMetadata();
            metadata.setProvider("openai"); metadata.setModelName(response.path("model").asText(request.getModelName()));
            metadata.setInputTokens(response.path("usage").path("input_tokens").isMissingNode() ? null : response.path("usage").path("input_tokens").asLong());
            metadata.setOutputTokens(response.path("usage").path("output_tokens").isMissingNode() ? null : response.path("usage").path("output_tokens").asLong());
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("gatewayType", "openai-responses");
            putIfPresent(attributes, "responseId", response.path("id").asText(null));
            metadata.setAttributes(attributes);
            OpenAiCompatibleSupport.parseOptionalSections(json, payload, result, metadata);
            result.setModelMetadata(metadata); return result;
        } catch (Exception e) { throw new IllegalStateException("Responses API assistant content was not valid JSON", e); }
    }

    private void putIfPresent(Map<String, Object> values, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            values.put(key, value);
        }
    }
}
