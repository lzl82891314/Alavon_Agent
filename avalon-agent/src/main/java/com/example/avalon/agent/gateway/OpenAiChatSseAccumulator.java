package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

final class OpenAiChatSseAccumulator {
    private static final List<String> REASONING_FIELDS = List.of(
            "reasoning_content", "reasoning", "reasoning_text", "thinking");

    private final ObjectMapper json;
    private final AgentTurnRequest request;
    private final ModelStreamEventPublisher events;
    private final String callId;
    private final long startedAt;
    private final StringBuilder content = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final Map<Integer, PendingToolCall> toolCalls = new LinkedHashMap<>();
    private ObjectNode metadata;
    private JsonNode usage;
    private JsonNode fullResponse;
    private String finishReason;
    private boolean terminalSeen;
    private Long firstReasoningDeltaMs;
    private Long firstContentDeltaMs;

    OpenAiChatSseAccumulator(ObjectMapper json,
                             AgentTurnRequest request,
                             ModelStreamEventPublisher events,
                             String callId,
                             long startedAt) {
        this.json = json;
        this.request = request;
        this.events = events;
        this.callId = callId;
        this.startedAt = startedAt;
    }

    void accept(SseFrame frame) {
        String data = frame == null ? null : frame.data();
        if (data == null || data.isBlank()) {
            return;
        }
        if ("[DONE]".equals(data.strip())) {
            terminalSeen = true;
            return;
        }
        JsonNode root = read(data);
        if (root.has("error")) {
            JsonNode error = root.path("error");
            String code = error.path("code").asText(null);
            String message = error.path("message").asText(null);
            String metadata = error.has("metadata") ? error.path("metadata").toString() : null;
            throw new OpenAiChatSseErrorException(code, message, metadata, error.toString());
        }
        JsonNode choice = root.path("choices").path(0);
        if (choice.path("message").isObject()) {
            fullResponse = root;
            publishFullMessage(choice.path("message"));
            terminalSeen = true;
            return;
        }
        captureMetadata(root);
        if (root.path("usage").isObject()) {
            usage = root.path("usage");
            events.usage(callId, request, usage.toString(), startedAt);
        }
        JsonNode delta = choice.path("delta");
        appendReasoning(delta);
        appendContent(delta.path("content"));
        appendToolCalls(delta.path("tool_calls"));
        if (choice.path("finish_reason").isTextual()) {
            finishReason = choice.path("finish_reason").asText();
            toolCalls.values().forEach(call -> events.toolComplete(callId, request, call.name, startedAt));
            terminalSeen = true;
        }
    }

    JsonNode response() {
        if (fullResponse != null) {
            return fullResponse;
        }
        if (!terminalSeen) {
            throw new IllegalStateException("Model SSE stream ended without a terminal event");
        }
        ObjectNode root = metadata == null ? json.createObjectNode() : metadata.deepCopy();
        if (usage != null) {
            root.set("usage", usage);
        }
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        choice.put("finish_reason", finishReason == null ? "stop" : finishReason);
        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", content.toString());
        if (!reasoning.isEmpty()) {
            message.put("reasoning_content", reasoning.toString());
        }
        if (!toolCalls.isEmpty()) {
            var calls = message.putArray("tool_calls");
            toolCalls.values().forEach(call -> calls.add(call.toJson(json)));
        }
        return root;
    }

    Long firstReasoningDeltaMs() {
        return firstReasoningDeltaMs;
    }

    Long firstContentDeltaMs() {
        return firstContentDeltaMs;
    }

    int reasoningChars() {
        return reasoning.length();
    }

    private void captureMetadata(JsonNode root) {
        if (metadata == null) {
            metadata = json.createObjectNode();
        }
        copyIfPresent(root, metadata, "id");
        copyIfPresent(root, metadata, "model");
        copyIfPresent(root, metadata, "service_tier");
        copyIfPresent(root, metadata, "system_fingerprint");
    }

    private void copyIfPresent(JsonNode source, ObjectNode target, String field) {
        if (source.has(field) && !source.path(field).isNull()) {
            target.set(field, source.path(field));
        }
    }

    private void appendReasoning(JsonNode delta) {
        for (String field : REASONING_FIELDS) {
            JsonNode value = delta.path(field);
            if (value.isTextual() && !value.asText().isEmpty()) {
                String text = value.asText();
                reasoning.append(text);
                if (firstReasoningDeltaMs == null) {
                    firstReasoningDeltaMs = elapsedMillis();
                }
                events.reasoning(callId, request, text, startedAt);
                return;
            }
        }
    }

    private void appendContent(JsonNode value) {
        if (value.isTextual() && !value.asText().isEmpty()) {
            String text = value.asText();
            content.append(text);
            if (firstContentDeltaMs == null) {
                firstContentDeltaMs = elapsedMillis();
            }
            events.content(callId, request, text, startedAt);
        }
    }

    private void publishFullMessage(JsonNode message) {
        for (String field : REASONING_FIELDS) {
            JsonNode value = message.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                firstReasoningDeltaMs = elapsedMillis();
                events.reasoning(callId, request, value.asText(), startedAt);
                break;
            }
        }
        JsonNode value = message.path("content");
        if (value.isTextual() && !value.asText().isBlank()) {
            firstContentDeltaMs = elapsedMillis();
            events.content(callId, request, value.asText(), startedAt);
        }
        for (JsonNode call : message.path("tool_calls")) {
            events.toolComplete(callId, request, call.path("function").path("name").asText("tool"), startedAt);
        }
    }

    private void appendToolCalls(JsonNode values) {
        if (!values.isArray()) return;
        for (JsonNode value : values) {
            int index = value.path("index").asInt(0);
            PendingToolCall call = toolCalls.computeIfAbsent(index, ignored -> new PendingToolCall());
            if (value.path("id").isTextual()) call.id = value.path("id").asText();
            JsonNode function = value.path("function");
            if (function.path("name").isTextual()) call.name = function.path("name").asText();
            String arguments = function.path("arguments").asText("");
            if (!arguments.isEmpty()) {
                call.arguments.append(arguments);
                events.toolArguments(callId, request, arguments, startedAt);
            }
        }
    }

    private JsonNode read(String data) {
        try {
            return json.readTree(data);
        } catch (Exception exception) {
            throw new IllegalStateException("Model SSE frame was not valid JSON", exception);
        }
    }

    private long elapsedMillis() {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private static final class PendingToolCall {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private ObjectNode toJson(ObjectMapper json) {
            ObjectNode result = json.createObjectNode();
            result.put("id", id);
            result.put("type", "function");
            result.putObject("function").put("name", name).put("arguments", arguments.toString());
            return result;
        }
    }
}
