package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.example.avalon.agent.model.MemoryUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adapter for the OpenAI Responses API; domain code remains dependent only on AgentGateway. */
@Component
public final class OpenAiResponsesGateway implements ModelProtocolAdapter {
    private final OpenAiHttpTransport transport;
    private final ModelProfileApiKeyResolver apiKeys;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public OpenAiResponsesGateway(OpenAiHttpTransport transport, ModelProfileApiKeyResolver apiKeys) { this.transport = transport; this.apiKeys = apiKeys; }

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
        JsonNode response = transport.postChatCompletion(OpenAiCompatibleSupport.responsesEndpointUri(baseUrl), Map.of("Authorization", "Bearer " + key), body(request), timeout);
        return parse(request, response);
    }

    private String body(AgentTurnRequest r) {
        ObjectNode root = json.createObjectNode();
        root.put("model", r.getModelName() == null || r.getModelName().isBlank() ? "gpt-5.2" : r.getModelName());
        root.put("instructions", "You control an Avalon player. Return exactly one JSON object and do not reveal chain-of-thought.");
        root.put("input", r.getPromptText());
        root.put("store", false);
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
            result.setActionJson(payload.path("action").isTextual() ? payload.path("action").asText() : json.writeValueAsString(payload.path("action")));
            if (payload.path("memoryUpdate").isObject()) {
                result.setMemoryUpdate(json.treeToValue(payload.path("memoryUpdate"), MemoryUpdate.class));
            }
            RawCompletionMetadata metadata = new RawCompletionMetadata();
            metadata.setProvider("openai"); metadata.setModelName(response.path("model").asText(request.getModelName()));
            metadata.setInputTokens(response.path("usage").path("input_tokens").isMissingNode() ? null : response.path("usage").path("input_tokens").asLong());
            metadata.setOutputTokens(response.path("usage").path("output_tokens").isMissingNode() ? null : response.path("usage").path("output_tokens").asLong());
            metadata.setAttributes(Map.of("gatewayType", "openai-responses", "responseId", response.path("id").asText()));
            result.setModelMetadata(metadata); return result;
        } catch (Exception e) { throw new IllegalStateException("Responses API returned invalid action JSON", e); }
    }
}
