package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AuditReason;
import com.example.avalon.agent.model.MemoryUpdate;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class OpenAiChatCompletionsGateway implements AgentGateway {
    private static final String GATEWAY_TYPE = "openai-compatible";
    private static final String DEFAULT_MODEL = "gpt-5.2";
    private static final String OPTIONAL_SECTION_WARNINGS = "optionalSectionWarnings";

    private final OpenAiHttpTransport transport;
    private final ModelProfileApiKeyResolver apiKeyResolver;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    public OpenAiChatCompletionsGateway(OpenAiHttpTransport transport,
                                        ModelProfileApiKeyResolver apiKeyResolver) {
        this.transport = transport;
        this.apiKeyResolver = apiKeyResolver;
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
        });
    }

    @Override
    public AgentTurnResult playTurn(AgentTurnRequest request) {
        RequestSettings settings = requestSettings(request);
        JsonNode response;
        try {
            response = transport.postChatCompletion(
                    OpenAiCompatibleSupport.endpointUri(settings.baseUrl()),
                    headers(settings),
                    requestBody(request),
                    settings.timeout()
            );
        } catch (RuntimeException exception) {
            throw transportException(request, exception);
        }
        return parseResponse(request, response);
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
        ArrayNode messages = root.putArray("messages");
        messages.addObject()
                .put("role", OpenAiCompatibleSupport.instructionRole(request.getProvider(), providerOptions))
                .put("content", developerPrompt(request));
        messages.addObject()
                .put("role", "user")
                .put("content", request.getPromptText());
        if (request.getTemperature() != null) {
            root.put("temperature", request.getTemperature());
        }
        int effectiveMaxTokens = OpenAiCompatibleSupport.effectiveMaxTokens(request.getProvider(), request.getMaxTokens());
        if (effectiveMaxTokens > 0) {
            root.put("max_completion_tokens", effectiveMaxTokens);
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
            result.setPrivateThought(textOrFallback(payload.path("privateThought"), analysis.reasoningDetailsPreview()));
            result.setActionJson(actionJson(payload));
            OptionalSectionParse<AuditReason> auditReason = parseOptionalSection(payload, "auditReason", AuditReason.class);
            if (auditReason.value() != null) {
                result.setAuditReason(auditReason.value());
            }
            OptionalSectionParse<MemoryUpdate> memoryUpdate = parseOptionalSection(payload, "memoryUpdate", MemoryUpdate.class);
            if (memoryUpdate.value() != null) {
                result.setMemoryUpdate(memoryUpdate.value());
            }
            RawCompletionMetadata metadata = metadata(request, response, choice, analysis);
            List<Map<String, Object>> optionalSectionWarnings = new ArrayList<>();
            optionalSectionWarnings.addAll(auditReason.warnings());
            optionalSectionWarnings.addAll(memoryUpdate.warnings());
            if (!optionalSectionWarnings.isEmpty()) {
                metadata.getAttributes().put(OPTIONAL_SECTION_WARNINGS, List.copyOf(optionalSectionWarnings));
            }
            result.setModelMetadata(metadata);
            return result;
        } catch (RuntimeException exception) {
            throw responseException(request, response, choice, analysis, exception);
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

    private <T> T convertValue(JsonNode node, Class<T> type) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to convert OpenAI-compatible payload section to " + type.getSimpleName(), exception);
        }
    }

    private <T> OptionalSectionParse<T> parseOptionalSection(JsonNode payload, String fieldName, Class<T> type) {
        JsonNode sectionNode = payload.get(fieldName);
        if (sectionNode == null || sectionNode.isNull() || sectionNode.isMissingNode()) {
            return OptionalSectionParse.empty();
        }
        if (!sectionNode.isObject()) {
            return OptionalSectionParse.warning(optionalSectionWarning(fieldName, "expected_json_object", sectionNode));
        }
        try {
            return OptionalSectionParse.value(convertValue(sectionNode, type));
        } catch (RuntimeException exception) {
            return OptionalSectionParse.warning(optionalSectionWarning(fieldName, "dto_conversion_failed", sectionNode));
        }
    }

    private Map<String, Object> optionalSectionWarning(String fieldName, String reason, JsonNode sectionNode) {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("field", fieldName);
        warning.put("reason", reason);
        warning.put("contentPreview", OpenAiCompatibleSupport.contentPreview(sectionNode == null ? null : sectionNode.toString()));
        return warning;
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

    private OpenAiCompatibleResponseException transportException(AgentTurnRequest request, RuntimeException exception) {
        if (exception instanceof OpenAiCompatibleResponseException compatibleResponseException) {
            return compatibleResponseException;
        }
        Map<String, Object> diagnostics = exception instanceof OpenAiCompatibleTransportException transportException
                ? transportException.diagnostics()
                : Map.of("failureDomain", "transport");
        return new OpenAiCompatibleResponseException(
                exception.getMessage() == null ? "OpenAI-compatible HTTP transport failed" : exception.getMessage(),
                exception,
                providerId(request),
                defaultModel(request.getModelName()),
                null,
                null,
                diagnostics
        );
    }

    private String developerPrompt(AgentTurnRequest request) {
        StringBuilder builder = new StringBuilder("""
                你正在控制一名阿瓦隆玩家。
                只返回一个 JSON 对象，不要输出 Markdown、代码块、<think>、项目符号或解释文字。
                最终输出的第一个字符必须是 {，最后一个字符必须是 }。
                优先返回最小合法 JSON，并把第一层键按 action、publicSpeech、privateThought、auditReason、memoryUpdate 的顺序写出。
                action 必填，且必须严格匹配用户提示里允许的动作类型，并保证字段完整合法。
                publicSpeech 只在当前阶段需要公开发言时才提供；如果提供，只写 1 到 2 句简短中文。
                privateThought 可以省略或写 null；如果提供，只写一句极短中文。
                auditReason 和 memoryUpdate 默认省略；只有在确有必要时才提供。
                如果提供 auditReason，它必须是 JSON 对象，字段只允许 goal、reasonSummary、confidence、beliefs。
                如果提供 memoryUpdate，它必须是 JSON 对象，字段只允许 suspicionDelta、trustDelta、observationsToAdd、commitmentsToAdd、inferredFactsToAdd、strategyMode、lastSummary。
                关于私有知识的强规则：
                - 只有 exactRoleId 明确告诉你的身份，才能当作确定事实写出来。
                - candidateRoleIds 只代表候选集合，不代表你已知真实身份。
                - 如果在 privateThought 或 auditReason.reasonSummary 中提到 candidateRoleIds，只能使用“怀疑 / 可能 / 更像 / 倾向 / 猜测”等不确定表达。
                - 绝不能写“P5是梅林”“P3就是莫甘娜”这类确定断言。
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

    record OptionalSectionParse<T>(T value, List<Map<String, Object>> warnings) {
        private static <T> OptionalSectionParse<T> empty() {
            return new OptionalSectionParse<>(null, List.of());
        }

        private static <T> OptionalSectionParse<T> value(T value) {
            return new OptionalSectionParse<>(value, List.of());
        }

        private static <T> OptionalSectionParse<T> warning(Map<String, Object> warning) {
            return new OptionalSectionParse<>(null, List.of(warning));
        }
    }
}
