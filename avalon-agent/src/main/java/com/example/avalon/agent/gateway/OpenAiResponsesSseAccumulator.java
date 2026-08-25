package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;

final class OpenAiResponsesSseAccumulator {
    private final ObjectMapper json;
    private final AgentTurnRequest request;
    private final ModelStreamEventPublisher events;
    private final String callId;
    private final long startedAt;
    private final StringBuilder content = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private ObjectNode response;
    private boolean terminalSeen;
    private Long firstReasoningDeltaMs;
    private Long firstContentDeltaMs;

    OpenAiResponsesSseAccumulator(ObjectMapper json,
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
        if (type.endsWith(".delta")) {
            String delta = root.path("delta").asText("");
            if (type.contains("reasoning")) {
                appendReasoning(delta);
            } else if (type.contains("output_text")) {
                appendContent(delta);
            }
            return;
        }
        if ("response.created".equals(type) || "response.in_progress".equals(type)) {
            captureResponse(root.path("response"));
            return;
        }
        if ("response.completed".equals(type)) {
            captureResponse(root.path("response"));
            terminalSeen = true;
            return;
        }
        if (type.endsWith(".failed") || "error".equals(type)) {
            throw new IllegalStateException("Responses SSE returned an error: " + root);
        }
        if (root.has("output") || root.has("output_text")) {
            captureResponse(root);
            publishFullResponse(root);
            terminalSeen = true;
        }
    }

    JsonNode response() {
        if (!terminalSeen) {
            throw new IllegalStateException("Responses SSE stream ended without response.completed");
        }
        ObjectNode result = response == null ? json.createObjectNode() : response.deepCopy();
        if (!content.isEmpty()) {
            result.put("output_text", content.toString());
        }
        if (!reasoning.isEmpty()) {
            result.put("reasoning_text", reasoning.toString());
        }
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

    private void captureResponse(JsonNode value) {
        if (value != null && value.isObject()) {
            response = (ObjectNode) value.deepCopy();
            if (value.path("usage").isObject()) {
                events.usage(callId, request, value.path("usage").toString(), startedAt);
            }
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

    private void publishFullResponse(JsonNode root) {
        String output = root.path("output_text").asText("");
        if (!output.isEmpty()) {
            appendContent(output);
        }
        String thought = root.path("reasoning_text").asText("");
        if (!thought.isEmpty()) {
            appendReasoning(thought);
        }
    }

    private JsonNode read(String data) {
        try {
            return json.readTree(data);
        } catch (Exception exception) {
            throw new IllegalStateException("Responses SSE frame was not valid JSON", exception);
        }
    }

    private long elapsedMillis() {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
