package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AgentLoopStep;
import com.example.avalon.agent.model.AgentModelTurn;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.example.avalon.agent.tool.ToolCall;
import com.example.avalon.agent.tool.ToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class OpenAiChatCompletionsGateway implements ModelProtocolAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiChatCompletionsGateway.class);
    private static final String GATEWAY_TYPE = "openai-compatible";
    private static final String DEFAULT_MODEL = "gpt-5.2";
    private static final String JSON_OBJECT_MESSAGE_MARKER = "Return one valid json object.";
    private final OpenAiHttpTransport transport;
    private final ModelProfileApiKeyResolver apiKeyResolver;
    private final ModelStreamEventPublisher streamEvents;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    public OpenAiChatCompletionsGateway(OpenAiHttpTransport transport,
                                        ModelProfileApiKeyResolver apiKeyResolver,
                                        ModelStreamEventPublisher streamEvents) {
        this.transport = transport;
        this.apiKeyResolver = apiKeyResolver;
        this.streamEvents = streamEvents;
    }

    OpenAiChatCompletionsGateway(OpenAiHttpTransport transport,
                                 Function<String, String> environmentLookup) {
        this(transport, (modelId, providerOptions) -> {
            String apiKey = OpenAiCompatibleSupport.stringOption(providerOptions, "apiKey");
            String apiKeyEnv = OpenAiCompatibleSupport.stringOption(providerOptions, "apiKeyEnv");
            if ((apiKey == null || apiKey.isBlank()) && apiKeyEnv != null && !apiKeyEnv.isBlank()) {
                apiKey = environmentLookup.apply(apiKeyEnv);
            }
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = environmentLookup.apply(ModelProfileSecretSupport.DEFAULT_SHARED_ENV_VAR);
            }
            return apiKey;
        }, ModelStreamEventPublisher.noop());
    }

    @Override
    public String protocolId() {
        return "OPENAI_COMPATIBLE_CHAT";
    }

    @Override
    public AgentTurnResult playTurn(AgentTurnRequest request) {
        AgentModelTurn turn = nextTurn(request);
        if (turn.requiresTools()) {
            throw new IllegalStateException("Default harness cannot execute model tool calls");
        }
        return turn.finalResult();
    }

    @Override
    public AgentModelTurn nextTurn(AgentTurnRequest request) {
        RequestSettings settings = requestSettings(request);
        long startedAt = System.nanoTime();
        String callId = streamEvents.started(request);
        LOGGER.info("model_call_start gameId={} playerId={} phase={} modelId={} provider={} endpoint={} timeoutMs={}",
                request.getGameId(), request.getPlayerId(), request.getPhase(), request.getModelId(),
                request.getProvider(), OpenAiCompatibleSupport.endpointUri(settings.baseUrl()), settings.timeout().toMillis());
        OpenAiChatSseAccumulator accumulator = new OpenAiChatSseAccumulator(
                objectMapper, request, streamEvents, callId, startedAt);
        SseHttpResponse streamResponse;
        try {
            streamResponse = transport.postEventStream(
                    OpenAiCompatibleSupport.endpointUri(settings.baseUrl()),
                    headers(settings),
                    requestBody(request),
                    settings.timeout(),
                    accumulator::accept
            );
        } catch (OpenAiCompatibleTransportException exception) {
            streamEvents.failed(callId, request, startedAt);
            LOGGER.error("model_call_failed gameId={} playerId={} phase={} modelId={} elapsedMs={} error={}",
                    request.getGameId(), request.getPlayerId(), request.getPhase(), request.getModelId(),
                    elapsedMillis(startedAt), exception.getMessage());
            throw OpenAiCompatibleSupport.transportResponseException(
                    request, exception, providerId(request), defaultModel(request.getModelName()));
        } catch (RuntimeException exception) {
            streamEvents.failed(callId, request, startedAt);
            throw OpenAiCompatibleSupport.protocolResponseException(
                    request, exception, providerId(request), defaultModel(request.getModelName()),
                    "invalid_stream_frame");
        }
        JsonNode response;
        try {
            response = accumulator.response();
        } catch (RuntimeException exception) {
            streamEvents.failed(callId, request, startedAt);
            throw OpenAiCompatibleSupport.protocolResponseException(
                    request, exception, providerId(request), defaultModel(request.getModelName()),
                    "stream_interrupted");
        }
        AgentModelTurn turn;
        try {
            turn = parseModelTurn(request, response);
            turn.modelMetadata().getAttributes().put("streaming", true);
            turn.modelMetadata().getAttributes().put("transportAttempts", streamResponse.transportAttempts());
            turn.modelMetadata().getAttributes().put("reasoningChars", accumulator.reasoningChars());
            putIfNotNull(turn.modelMetadata().getAttributes(),
                    "firstReasoningDeltaMs", accumulator.firstReasoningDeltaMs());
            putIfNotNull(turn.modelMetadata().getAttributes(),
                    "firstContentDeltaMs", accumulator.firstContentDeltaMs());
        } catch (RuntimeException exception) {
            streamEvents.failed(callId, request, startedAt);
            throw exception;
        }
        streamEvents.completed(callId, request, startedAt, streamResponse.transportAttempts());
        LOGGER.info("model_call_response gameId={} playerId={} phase={} modelId={} elapsedMs={} streaming=true transportAttempts={}",
                request.getGameId(), request.getPlayerId(), request.getPhase(), request.getModelId(),
                elapsedMillis(startedAt), streamResponse.transportAttempts());
        return turn;
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private Map<String, String> headers(RequestSettings settings) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + settings.apiKey());
        if (settings.organization() != null && !settings.organization().isBlank()) {
            headers.put("OpenAI-Organization", settings.organization());
        }
        if (settings.project() != null && !settings.project().isBlank()) {
            headers.put("OpenAI-Project", settings.project());
        }
        return headers;
    }

    private String requestBody(AgentTurnRequest request) {
        Map<String, Object> providerOptions = OpenAiCompatibleSupport.effectiveProviderOptions(
                request.getProvider(),
                request.getProviderOptions()
        );
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", defaultModel(request.getModelName()));
        root.put("stream", true);
        root.putObject("stream_options").put("include_usage", true);
        ArrayNode messages = root.putArray("messages");
        messages.addObject()
                .put("role", OpenAiCompatibleSupport.instructionRole(request.getProvider(), providerOptions))
                .put("content", developerPrompt(request)
                        + (usesJsonObjectResponseFormat(providerOptions) ? System.lineSeparator()
                        + JSON_OBJECT_MESSAGE_MARKER : ""));
        messages.addObject()
                .put("role", "user")
                .put("content", request.getPromptText());
        appendLoopMessages(messages, request);
        if (!request.getTools().isEmpty()) {
            ToolCallingJsonSupport.addOpenAiTools(objectMapper, root, request.getTools());
        }
        if (request.getTemperature() != null) {
            root.put("temperature", request.getTemperature());
        }
        for (Map.Entry<String, Object> entry : providerOptions.entrySet()) {
            if (!OpenAiCompatibleSupport.shouldForwardProviderOption(entry.getKey())) {
                continue;
            }
            root.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize OpenAI-compatible chat completion request", exception);
        }
    }

    private AgentTurnResult parseResponse(AgentTurnRequest request, JsonNode response) {
        JsonNode choice = response.path("choices").path(0);
        if (choice.isMissingNode()) {
            throw new IllegalStateException("OpenAI-compatible response did not include any choices");
        }

        JsonNode message = choice.path("message");
        String refusal = textOrNull(message.path("refusal"));
        if (refusal != null) {
            throw new IllegalStateException("OpenAI-compatible completion refused the request: " + refusal);
        }

        OpenAiCompatibleMessageAnalysis analysis = OpenAiCompatibleSupport.analyzeAssistantMessage(message);
        try {
            JsonNode payload = OpenAiCompatibleSupport.readJson(objectMapper, analysis);

            AgentTurnResult result = new AgentTurnResult();
            result.setPublicSpeech(textOrNull(payload.path("publicSpeech")));
            result.setPrivateThought(textOrNull(payload.path("privateThought")));
            result.setActionJson(actionJson(payload));
            RawCompletionMetadata metadata = metadata(request, response, choice, analysis);
            OpenAiCompatibleSupport.parseOptionalSections(objectMapper, payload, result, metadata);
            result.setModelMetadata(metadata);
            return result;
        } catch (RuntimeException exception) {
            throw responseException(request, response, choice, analysis, exception);
        }
    }

    private AgentModelTurn parseModelTurn(AgentTurnRequest request, JsonNode response) {
        JsonNode choice = response.path("choices").path(0);
        if (choice.isMissingNode()) {
            throw new IllegalStateException("OpenAI-compatible response did not include any choices");
        }
        JsonNode message = choice.path("message");
        JsonNode nativeCalls = message.path("tool_calls");
        if (nativeCalls.isArray() && !nativeCalls.isEmpty()) {
            OpenAiCompatibleMessageAnalysis analysis = OpenAiCompatibleSupport.analyzeAssistantMessage(message);
            RawCompletionMetadata metadata = metadata(request, response, choice, analysis);
            List<ToolCall> calls = new java.util.ArrayList<>();
            for (JsonNode call : nativeCalls) {
                JsonNode function = call.path("function");
                calls.add(new ToolCall(call.path("id").asText(), function.path("name").asText(),
                        ToolCallingJsonSupport.arguments(objectMapper, function.path("arguments"))));
            }
            return AgentModelTurn.tools(calls, metadata);
        }
        return AgentModelTurn.completed(parseResponse(request, response));
    }

    private void appendLoopMessages(ArrayNode messages, AgentTurnRequest request) {
        for (AgentLoopStep step : request.getLoopSteps()) {
            ObjectNode assistant = messages.addObject();
            assistant.put("role", "assistant");
            ArrayNode calls = assistant.putArray("tool_calls");
            step.toolCalls().forEach(call -> calls.add(ToolCallingJsonSupport.openAiToolCall(objectMapper, call)));
            for (ToolResult result : step.toolResults()) {
                messages.addObject().put("role", "tool").put("tool_call_id", result.callId())
                        .put("content", ToolCallingJsonSupport.resultJson(objectMapper, result));
            }
        }
    }

    private String actionJson(JsonNode payload) {
        JsonNode actionNode = payload.get("action");
        if (actionNode == null || actionNode.isNull() || actionNode.isMissingNode()) {
            throw new IllegalStateException("OpenAI-compatible response did not include an action object");
        }
        if (actionNode.isTextual()) {
            return actionNode.asText();
        }
        try {
            return objectMapper.writeValueAsString(actionNode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize action payload from OpenAI-compatible response", exception);
        }
    }

    private RawCompletionMetadata metadata(AgentTurnRequest request,
                                           JsonNode response,
                                           JsonNode choice,
                                           OpenAiCompatibleMessageAnalysis analysis) {
        RawCompletionMetadata metadata = new RawCompletionMetadata();
        metadata.setProvider(providerId(request));
        metadata.setModelName(textOrFallback(response.path("model"), defaultModel(request.getModelName())));
        metadata.setInputTokens(longOrNull(response.path("usage").path("prompt_tokens")));
        metadata.setOutputTokens(longOrNull(response.path("usage").path("completion_tokens")));

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("gatewayType", GATEWAY_TYPE);
        putIfNotNull(attributes, "completionId", textOrNull(response.path("id")));
        putIfNotNull(attributes, "finishReason", textOrNull(choice.path("finish_reason")));
        putIfNotNull(attributes, "serviceTier", textOrNull(response.path("service_tier")));
        putIfNotNull(attributes, "systemFingerprint", textOrNull(response.path("system_fingerprint")));
        if (analysis != null) {
            attributes.putAll(analysis.diagnostics());
        }
        metadata.setAttributes(attributes);
        return metadata;
    }

    private RequestSettings requestSettings(AgentTurnRequest request) {
        Map<String, Object> providerOptions = request.getProviderOptions();
        String apiKey = apiKeyResolver.resolveApiKey(request.getModelId(), providerOptions);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    ModelProfileSecretSupport.missingApiKeyMessage(providerId(request), request.getModelId())
            );
        }
        String baseUrl = OpenAiCompatibleSupport.stringOption(providerOptions, "baseUrl");
        String organization = OpenAiCompatibleSupport.stringOption(providerOptions, "organization");
        String project = OpenAiCompatibleSupport.stringOption(providerOptions, "project");
        Duration timeout = timeout(request.getProvider(), providerOptions.get("timeoutMillis"));
        return new RequestSettings(
                baseUrl == null || baseUrl.isBlank() ? OpenAiCompatibleSupport.DEFAULT_BASE_URL : baseUrl,
                apiKey,
                organization,
                project,
                timeout
        );
    }

    private Duration timeout(String provider, Object rawTimeoutMillis) {
        return OpenAiCompatibleSupport.effectiveTimeout(provider, rawTimeoutMillis);
    }

    private String defaultModel(String modelName) {
        return modelName == null || modelName.isBlank() ? DEFAULT_MODEL : modelName;
    }

    private String providerId(AgentTurnRequest request) {
        if (request == null) {
            return "openai";
        }
        return OpenAiCompatibleSupport.providerId(request.getProvider());
    }

    private OpenAiCompatibleResponseException responseException(AgentTurnRequest request,
                                                                JsonNode response,
                                                                JsonNode choice,
                                                                OpenAiCompatibleMessageAnalysis analysis,
                                                                RuntimeException exception) {
        if (exception instanceof OpenAiCompatibleResponseException compatibleResponseException) {
            return compatibleResponseException;
        }
        String finishReason = textOrNull(choice.path("finish_reason"));
        String message = exception.getMessage() == null
                ? OpenAiCompatibleSupport.invalidJsonMessage(analysis)
                : exception.getMessage();
        if (finishReason != null && !message.contains("finishReason=")) {
            message = message + " [finishReason=" + finishReason + "]";
        }
        return new OpenAiCompatibleResponseException(
                message,
                exception,
                providerId(request),
                textOrFallback(response.path("model"), defaultModel(request.getModelName())),
                finishReason,
                analysis
        );
    }

    private String developerPrompt(AgentTurnRequest request) {
        StringBuilder builder = new StringBuilder("""
                你负责一个信息不完全的阿瓦隆战略 Agent。
                严格区分规则事实、其他玩家的公开主张和自己的私有信念；公开主张不自动为真。
                根据可见 sequence 证据更新概率、跨回合目标、公开承诺和叙事计划。
                角色策略允许时可以进行游戏内隐瞒或误导，但不得泄露私有身份知识或访问不可见信息。
                所有公开发言和 privateThought 必须使用简体中文；JSON 字段名和枚举仍按契约输出。
                返回一个 JSON 对象，必须包含合法 action；memoryUpdate 是可选认知草稿，无法保证合法时应省略；不要输出原始思维链或 JSON 外文本。
                """.strip());
        if ("minimax".equals(providerId(request))) {
            builder.append(System.lineSeparator())
                    .append("""
                            当前 provider 的兼容要求更严格：
                            - 不要输出项目符号
                            - 如果当前阶段只允许一个动作类型，action.actionType 必须严格等于该类型
                            """.strip());
        }
        return builder.toString();
    }

    private boolean usesJsonObjectResponseFormat(Map<String, Object> providerOptions) {
        Object format = providerOptions.get("response_format");
        if (!(format instanceof Map<?, ?> values)) return false;
        Object type = values.get("type");
        return "json_object".equalsIgnoreCase(type == null ? null : String.valueOf(type));
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String textOrFallback(JsonNode node, String fallback) {
        String text = textOrNull(node);
        return text == null ? fallback : text;
    }

    private Long longOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asLong();
    }

    private void putIfNotNull(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    record RequestSettings(
            String baseUrl,
            String apiKey,
            String organization,
            String project,
            Duration timeout
    ) {
    }

}
