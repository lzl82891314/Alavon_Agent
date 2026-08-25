package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;

final class AnthropicSseAccumulator {
    private final ObjectMapper json;
    private final AgentTurnRequest request;
    private final ModelStreamEventPublisher events;
    private final String callId;
    private final long startedAt;
    private final StringBuilder content = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private ObjectNode message;
    private String stopReason;
    private boolean terminalSeen;
    private Long firstReasoningDeltaMs;
    private Long firstContentDeltaMs;

    AnthropicSseAccumulator(ObjectMapper json,
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
        if (data == null || data.isBlank() || "[DONE]".equals(data.strip())) {
            return;
        }
        JsonNode root = read(data);
        String type = root.path("type").asText(frame.event() == null ? "" : frame.event());
        switch (type) {
            case "message_start" -> captureMessage(root.path("message"));
            case "content_block_start" -> appendBlock(root.path("content_block"));
            case "content_block_delta" -> appendDelta(root.path("delta"));
            case "message_delta" -> captureMessageDelta(root);
            case "message_stop" -> terminalSeen = true;
            case "error" -> throw new IllegalStateException("Anthropic SSE returned an error: " + root.path("error"));
            default -> {
                if (root.path("content").isArray()) {
                    captureMessage(root);
                    publishFullMessage(root);
                    terminalSeen = true;
                }
            }
        }
    }

    JsonNode response() {
        if (!terminalSeen) {
            throw new IllegalStateException("Anthropic SSE stream ended without message_stop");
        }
        ObjectNode result = message == null ? json.createObjectNode() : message.deepCopy();
        if (stopReason != null) {
            result.put("stop_reason", stopReason);
        }
        result.putArray("content").addObject().put("type", "text").put("text", content.toString());
        return result;
    }

    String reasoning() {
        return reasoning.isEmpty() ? null : reasoning.toString();
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

    private void captureMessage(JsonNode value) {
        if (value != null && value.isObject()) {
            message = (ObjectNode) value.deepCopy();
            if (value.path("usage").isObject()) {
                events.usage(callId, request, value.path("usage").toString(), startedAt);
            }
        }
    }

    private void appendBlock(JsonNode block) {
        if ("text".equals(block.path("type").asText())) {
            appendContent(block.path("text").asText(""));
        } else if ("thinking".equals(block.path("type").asText())) {
            appendReasoning(block.path("thinking").asText(""));
        }
    }

    private void appendDelta(JsonNode delta) {
        String type = delta.path("type").asText("");
        if ("text_delta".equals(type)) {
            appendContent(delta.path("text").asText(""));
        } else if ("thinking_delta".equals(type)) {
            appendReasoning(delta.path("thinking").asText(""));
        }
    }

    private void captureMessageDelta(JsonNode root) {
        if (root.path("delta").path("stop_reason").isTextual()) {
            stopReason = root.path("delta").path("stop_reason").asText();
        }
        if (root.path("usage").isObject()) {
            if (message == null) {
                message = json.createObjectNode();
            }
            ObjectNode usage = message.withObject("/usage");
            root.path("usage").fields().forEachRemaining(entry -> usage.set(entry.getKey(), entry.getValue()));
            events.usage(callId, request, root.path("usage").toString(), startedAt);
        }
    }

    private void publishFullMessage(JsonNode root) {
        for (JsonNode block : root.path("content")) {
            appendBlock(block);
        }
    }

    private void appendReasoning(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        reasoning.append(delta);
        if (firstReasoningDeltaMs == null) {
            firstReasoningDeltaMs = elapsedMillis();
        }
        events.reasoning(callId, request, delta, startedAt);
    }

    private void appendContent(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        content.append(delta);
        if (firstContentDeltaMs == null) {
            firstContentDeltaMs = elapsedMillis();
        }
        events.content(callId, request, delta, startedAt);
    }

    private JsonNode read(String data) {
        try {
            return json.readTree(data);
        } catch (Exception exception) {
            throw new IllegalStateException("Anthropic SSE frame was not valid JSON", exception);
        }
    }

    private long elapsedMillis() {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
