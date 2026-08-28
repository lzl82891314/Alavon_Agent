package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public final class ModelStreamEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelStreamEventPublisher.class);
    private final List<ModelStreamEventListener> listeners;

    public ModelStreamEventPublisher(List<ModelStreamEventListener> listeners) {
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
    }

    public static ModelStreamEventPublisher noop() {
        return new ModelStreamEventPublisher(List.of());
    }

    public String started(AgentTurnRequest request) {
        String callId = UUID.randomUUID().toString();
        publish(callId, request, ModelStreamEventType.STARTED, null, 0L, null);
        return callId;
    }

    public void reasoning(String callId, AgentTurnRequest request, String delta, long startedAt) {
        publish(callId, request, ModelStreamEventType.REASONING_DELTA, delta, elapsedMillis(startedAt), null);
    }

    public void content(String callId, AgentTurnRequest request, String delta, long startedAt) {
        publish(callId, request, ModelStreamEventType.CONTENT_DELTA, delta, elapsedMillis(startedAt), null);
    }

    public void toolArguments(String callId, AgentTurnRequest request, String delta, long startedAt) {
        publish(callId, request, ModelStreamEventType.TOOL_CALL_ARGUMENT_DELTA, delta,
                elapsedMillis(startedAt), null);
    }

    public void toolComplete(String callId, AgentTurnRequest request, String toolName, long startedAt) {
        publish(callId, request, ModelStreamEventType.TOOL_CALL_COMPLETE, toolName,
                elapsedMillis(startedAt), null);
    }

    public void usage(String callId, AgentTurnRequest request, String usageJson, long startedAt) {
        publish(callId, request, ModelStreamEventType.USAGE, usageJson, elapsedMillis(startedAt), null);
    }

    public void completed(String callId, AgentTurnRequest request, long startedAt, int transportAttempts) {
        publish(callId, request, ModelStreamEventType.COMPLETED, null, elapsedMillis(startedAt), transportAttempts);
    }

    public void failed(String callId, AgentTurnRequest request, long startedAt) {
        publish(callId, request, ModelStreamEventType.FAILED, null, elapsedMillis(startedAt), null);
    }

    private void publish(String callId,
                         AgentTurnRequest request,
                         ModelStreamEventType type,
                         String delta,
                         long elapsedMillis,
                         Integer transportAttempts) {
        ModelStreamEvent event = new ModelStreamEvent(
                callId,
                request == null ? null : request.getGameId(),
                request == null ? null : request.getPlayerId(),
                request == null ? null : request.getPhase(),
                request == null ? null : request.getModelId(),
                request == null ? null : request.getModelName(),
                request == null ? null : request.getProvider(),
                request == null ? null : request.getRoleId(),
                type,
                delta,
                elapsedMillis,
                transportAttempts
        );
        for (ModelStreamEventListener listener : listeners) {
            try {
                listener.onModelStreamEvent(event);
            } catch (RuntimeException exception) {
                LOGGER.warn("model_stream_listener_failed listener={} type={} error={}",
                        listener.getClass().getName(), type, exception.getMessage());
            }
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
