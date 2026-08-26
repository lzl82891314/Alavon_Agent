package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AuditReason;
import com.example.avalon.agent.model.MemoryUpdate;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenAiCompatibleSupport {
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration HIGH_LATENCY_PROVIDER_TIMEOUT = Duration.ofSeconds(60);
    private static final Set<String> SYSTEM_INSTRUCTION_PROVIDERS = Set.of("minimax");
    private static final Set<String> REASONING_SPLIT_PROVIDERS = Set.of("minimax");
    private static final Set<String> HIGH_LATENCY_TIMEOUT_PROVIDERS = Set.of("minimax", "glm", "claude", "qwen");
    private static final Set<String> LOCAL_OPTION_KEYS = Set.of(
            "apiKey",
            "apiKeyEnv",
            "baseUrl",
            "organization",
            "project",
            "timeoutMillis",
            "instructionRole"
    );
    private static final Set<String> RESERVED_REQUEST_KEYS = Set.of(
            "model",
            "messages",
            "temperature",
            "max_completion_tokens",
            "stream",
            "stream_options"
    );
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("(?s)```(?:json)?\\s*(.*?)```");
    private static final int PREVIEW_LIMIT = 160;

    private OpenAiCompatibleSupport() {
    }

    public static String providerId(String provider) {
        if (provider == null || provider.isBlank()) {
            return "openai";
        }
        return provider.strip().toLowerCase(Locale.ROOT);
    }

    public static URI endpointUri(String baseUrl) {
        String normalizedBaseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        String normalized = normalizedBaseUrl.endsWith("/")
                ? normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1)
                : normalizedBaseUrl;
        if (normalized.toLowerCase(Locale.ROOT).endsWith("/chat/completions")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/chat/completions");
    }

    public static URI responsesEndpointUri(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        normalized = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        return URI.create(normalized.toLowerCase(Locale.ROOT).endsWith("/responses") ? normalized : normalized + "/responses");
    }

    public static URI anthropicMessagesEndpointUri(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank() ? "https://api.anthropic.com/v1" : baseUrl;
        normalized = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        return URI.create(normalized.toLowerCase(Locale.ROOT).endsWith("/messages") ? normalized : normalized + "/messages");
    }

    public static boolean usesResponsesProtocol(String protocol) {
        return "OPENAI_RESPONSES".equalsIgnoreCase(protocol);
    }

    public static String instructionRole(String provider, Map<String, Object> providerOptions) {
        String override = stringOption(providerOptions, "instructionRole");
        if (override != null) {
            String normalized = override.strip().toLowerCase(Locale.ROOT);
            if (!"system".equals(normalized) && !"developer".equals(normalized)) {
                throw new IllegalArgumentException("instructionRole must be either 'system' or 'developer'");
            }
            return normalized;
        }
        return SYSTEM_INSTRUCTION_PROVIDERS.contains(providerId(provider)) ? "system" : "developer";
    }

    public static Map<String, Object> effectiveProviderOptions(String provider, Map<String, Object> providerOptions) {
        Map<String, Object> effective = new LinkedHashMap<>();
        if (providerOptions != null && !providerOptions.isEmpty()) {
            effective.putAll(providerOptions);
        }
        if (REASONING_SPLIT_PROVIDERS.contains(providerId(provider))
                && !effective.containsKey("reasoning_split")) {
            effective.put("reasoning_split", true);
        }
        return effective;
    }

    public static Duration effectiveTimeout(String provider, Object rawTimeoutMillis) {
        if (rawTimeoutMillis instanceof Number number) {
            return Duration.ofMillis(number.longValue());
        }
        if (rawTimeoutMillis instanceof String text && !text.isBlank()) {
            return Duration.ofMillis(Long.parseLong(text));
        }
        return HIGH_LATENCY_TIMEOUT_PROVIDERS.contains(providerId(provider))
                ? HIGH_LATENCY_PROVIDER_TIMEOUT
                : DEFAULT_TIMEOUT;
    }

    public static OpenAiCompatibleMessageAnalysis analyzeAssistantMessage(JsonNode message) {
        String content = assistantContentOrNull(message.path("content"));
        JsonNode reasoningNode = message.has("reasoning_details")
                ? message.path("reasoning_details")
                : message.path("reasoning_content");
        String reasoningContent = reasoningContent(reasoningNode);
        String reasoningPreview = contentPreview(reasoningContent);
        boolean contentPresent = content != null && !content.isBlank();
        boolean reasoningPresent = reasoningPreview != null && !reasoningPreview.isBlank();
        if (!contentPresent) {
            String normalizedReasoning = reasoningContent == null ? null : reasoningContent.strip();
            if (normalizedReasoning != null
                    && normalizedReasoning.startsWith("{")
                    && looksLikeJsonObject(normalizedReasoning)) {
                return new OpenAiCompatibleMessageAnalysis(
                        false,
                        true,
                        "reasoning_json_object",
                        null,
                        reasoningContent,
                        reasoningPreview,
                        normalizedReasoning
                );
            }
            return new OpenAiCompatibleMessageAnalysis(
                    false,
                    reasoningPresent,
                    reasoningPresent ? "reasoning_only" : "missing_content",
                    null,
                    reasoningContent,
                    reasoningPreview,
                    null
            );
        }

        String originalPreview = contentPreview(content);
        String normalized = normalizeAssistantContent(content);
        if (normalized.startsWith("{") && looksLikeJsonObject(normalized)) {
            return new OpenAiCompatibleMessageAnalysis(
                    true,
                    reasoningPresent,
                    content.strip().startsWith("<think>") ? "think_prefixed_json" : "json_object",
                    originalPreview,
                    reasoningContent,
                    reasoningPreview,
                    normalized
            );
        }
        if (isLikelyTruncatedJson(normalized)) {
            return new OpenAiCompatibleMessageAnalysis(
                    true,
                    reasoningPresent,
                    "truncated_json_candidate",
                    originalPreview,
                    reasoningContent,
                    reasoningPreview,
                    null
            );
        }

        String codeBlock = extractCodeBlock(normalized);
        if (codeBlock != null && looksLikeJsonObject(codeBlock)) {
            return new OpenAiCompatibleMessageAnalysis(
                    true,
                    reasoningPresent,
                    "markdown_code_block",
                    originalPreview,
                    reasoningContent,
                    reasoningPreview,
                    codeBlock
            );
        }
        if (codeBlock != null && isLikelyTruncatedJson(codeBlock)) {
            return new OpenAiCompatibleMessageAnalysis(
                    true,
                    reasoningPresent,
                    "truncated_json_candidate",
                    originalPreview,
                    reasoningContent,
                    reasoningPreview,
                    null
            );
        }

        String embeddedJson = extractFirstJsonObject(normalized);
        if (embeddedJson != null) {
            return new OpenAiCompatibleMessageAnalysis(
                    true,
                    reasoningPresent,
                    "embedded_json_object",
                    originalPreview,
                    reasoningContent,
                    reasoningPreview,
                    embeddedJson
            );
        }

        String shape = normalized.contains("```") ? "markdown_explanation" : "plain_text";
        return new OpenAiCompatibleMessageAnalysis(
                true,
                reasoningPresent,
                shape,
                originalPreview,
                reasoningContent,
                reasoningPreview,
                null
        );
    }

    public static JsonNode readJson(ObjectMapper objectMapper, OpenAiCompatibleMessageAnalysis analysis) {
        if (analysis == null || !analysis.hasJsonCandidate()) {
            throw new IllegalStateException(invalidJsonMessage(analysis));
        }
        try {
            return objectMapper.readTree(analysis.jsonCandidate());
        } catch (Exception exception) {
            throw new IllegalStateException(invalidJsonMessage(analysis), exception);
        }
    }

    public static String invalidJsonMessage(OpenAiCompatibleMessageAnalysis analysis) {
        if (analysis == null) {
            return "OpenAI-compatible assistant content was not valid JSON";
        }
        if (!analysis.contentPresent()) {
            if (analysis.reasoningDetailsPresent()) {
                return "OpenAI-compatible assistant content was empty (shape="
                        + analysis.assistantContentShape()
                        + ", reasoningPreview="
                        + contentPreview(analysis.reasoningDetailsPreview())
                        + ")";
            }
            return "OpenAI-compatible response did not include assistant content";
        }
        if ("truncated_json_candidate".equals(analysis.assistantContentShape())) {
            return "OpenAI-compatible assistant content looked like truncated JSON (shape="
                    + analysis.assistantContentShape()
                    + ", bodyPreview="
                    + contentPreview(analysis.assistantContentPreview())
                    + ")";
        }
        return "OpenAI-compatible assistant content was not valid JSON (shape="
                + analysis.assistantContentShape()
                + ", bodyPreview="
                + contentPreview(analysis.assistantContentPreview())
                + ")";
    }

    public static String contentPreview(String content) {
        if (content == null || content.isBlank()) {
            return "<empty>";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_LIMIT) + "...";
    }

    public static String stringOption(Map<String, Object> options, String key) {
        if (options == null) {
            return null;
        }
        Object value = options.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static boolean shouldForwardProviderOption(String key) {
        return key != null && !LOCAL_OPTION_KEYS.contains(key) && !RESERVED_REQUEST_KEYS.contains(key);
    }

    public static void parseOptionalSections(ObjectMapper objectMapper,
                                             JsonNode payload,
                                             AgentTurnResult result,
                                             RawCompletionMetadata metadata) {
        List<Map<String, Object>> warnings = new ArrayList<>();
        result.setAuditReason(parseOptionalSection(
                objectMapper, payload, "auditReason", AuditReason.class, warnings));
        result.setMemoryUpdate(parseOptionalSection(
                objectMapper, payload, "memoryUpdate", MemoryUpdate.class, warnings));
        if (!warnings.isEmpty()) {
            metadata.getAttributes().put("optionalSectionWarnings", List.copyOf(warnings));
        }
    }

    public static OpenAiCompatibleResponseException transportResponseException(AgentTurnRequest request,
                                                                                RuntimeException exception,
                                                                                String defaultProvider,
                                                                                String defaultModel) {
        if (exception instanceof OpenAiCompatibleResponseException responseException) {
            return responseException;
        }
        Map<String, Object> diagnostics = exception instanceof OpenAiCompatibleTransportException transportException
                ? transportException.diagnostics()
                : Map.of("failureDomain", "transport");
        String provider = request == null || request.getProvider() == null || request.getProvider().isBlank()
                ? defaultProvider
                : providerId(request.getProvider());
        String model = request == null || request.getModelName() == null || request.getModelName().isBlank()
                ? defaultModel
                : request.getModelName();
        return new OpenAiCompatibleResponseException(
                exception.getMessage() == null ? "Model SSE transport failed" : exception.getMessage(),
                exception,
                provider == null ? "unknown" : provider,
                model == null ? "unknown" : model,
                null,
                null,
                diagnostics
        );
    }

    public static OpenAiCompatibleResponseException protocolResponseException(AgentTurnRequest request,
                                                                               RuntimeException exception,
                                                                               String defaultProvider,
                                                                               String defaultModel,
                                                                               String failureKind) {
        if (exception instanceof OpenAiCompatibleResponseException responseException) {
            return responseException;
        }
        String provider = request == null || request.getProvider() == null || request.getProvider().isBlank()
                ? defaultProvider
                : providerId(request.getProvider());
        String model = request == null || request.getModelName() == null || request.getModelName().isBlank()
                ? defaultModel
                : request.getModelName();
        return new OpenAiCompatibleResponseException(
                exception.getMessage() == null ? "Model SSE response was invalid" : exception.getMessage(),
                exception,
                provider == null ? "unknown" : provider,
                model == null ? "unknown" : model,
                null,
                null,
                Map.of("failureDomain", "protocol", "failureKind", failureKind)
        );
    }

    private static <T> T parseOptionalSection(ObjectMapper objectMapper,
                                              JsonNode payload,
                                              String fieldName,
                                              Class<T> type,
                                              List<Map<String, Object>> warnings) {
        JsonNode section = payload.get(fieldName);
        if (section == null || section.isNull() || section.isMissingNode()) {
            return null;
        }
        if (!section.isObject()) {
            warnings.add(optionalSectionWarning(fieldName, "expected_json_object", section));
            return null;
        }
        try {
            return objectMapper.treeToValue(section, type);
        } catch (Exception exception) {
            warnings.add(optionalSectionWarning(fieldName, "dto_conversion_failed", section));
            return null;
        }
    }

    private static Map<String, Object> optionalSectionWarning(String fieldName,
                                                              String reason,
                                                              JsonNode section) {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("field", fieldName);
        warning.put("reason", reason);
        warning.put("contentPreview", contentPreview(section == null ? null : section.toString()));
        return Map.copyOf(warning);
    }

    private static String assistantContentOrNull(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull() || contentNode.isMissingNode()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : contentNode) {
                appendAssistantContent(builder, item);
            }
            return builder.isEmpty() ? null : builder.toString();
        }
        return contentNode.toString();
    }

    private static String normalizeAssistantContent(String content) {
        if (content == null) {
            return null;
        }
        String normalized = content.trim();
        if (!normalized.startsWith("<think>")) {
            return normalized;
        }
        int endIndex = normalized.indexOf("</think>");
        if (endIndex >= 0) {
            String remainder = normalized.substring(endIndex + "</think>".length()).trim();
            return remainder.isBlank() ? normalized : remainder;
        }
        return normalized.substring("<think>".length()).trim();
    }

    private static String extractCodeBlock(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String code = matcher.group(1);
        return code == null || code.isBlank() ? null : code.trim();
    }

    private static boolean looksLikeJsonObject(String content) {
        if (content == null) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private static boolean isLikelyTruncatedJson(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.startsWith("{")
                && !trimmed.endsWith("}")
                && extractFirstJsonObject(trimmed) == null
                && trimmed.contains("\":");
    }

    private static String extractFirstJsonObject(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (start < 0) {
                if (current == '{') {
                    start = index;
                    depth = 1;
                }
                continue;
            }
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(start, index + 1).trim();
                }
            }
        }
        return null;
    }

    private static String reasoningContent(JsonNode reasoningNode) {
        if (reasoningNode == null || reasoningNode.isNull() || reasoningNode.isMissingNode()) {
            return null;
        }
        ArrayList<String> chunks = new ArrayList<>();
        collectReasoningText(reasoningNode, chunks);
        String combined = String.join(" ", chunks).trim();
        if (!combined.isBlank()) {
            return combined;
        }
        return reasoningNode.toString();
    }

    private static void collectReasoningText(JsonNode node, ArrayList<String> chunks) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isTextual()) {
            String value = node.asText();
            if (value != null && !value.isBlank()) {
                chunks.add(value);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectReasoningText(item, chunks);
            }
            return;
        }
        if (node.isObject()) {
            if (node.has("text")) {
                collectReasoningText(node.get("text"), chunks);
                return;
            }
            if (node.has("content")) {
                collectReasoningText(node.get("content"), chunks);
                return;
            }
            node.elements().forEachRemaining(child -> collectReasoningText(child, chunks));
        }
    }

    private static void appendAssistantContent(StringBuilder builder, JsonNode item) {
        if (item == null || item.isNull() || item.isMissingNode()) {
            return;
        }
        if (item.isTextual()) {
            builder.append(item.asText());
            return;
        }
        String itemType = item.path("type").asText("");
        if ("refusal".equals(itemType)) {
            throw new IllegalStateException("OpenAI-compatible completion refused the request: "
                    + item.path("refusal").asText(""));
        }
        if (item.has("text")) {
            appendAssistantContent(builder, item.get("text"));
            return;
        }
        if (item.has("content")) {
            appendAssistantContent(builder, item.get("content"));
            return;
        }
        builder.append(item.toString());
    }
}
