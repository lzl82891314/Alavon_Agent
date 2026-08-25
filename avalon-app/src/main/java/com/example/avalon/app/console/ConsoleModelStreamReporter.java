package com.example.avalon.app.console;

import com.example.avalon.agent.gateway.ModelStreamEvent;
import com.example.avalon.agent.gateway.ModelStreamEventListener;
import com.example.avalon.agent.gateway.ModelStreamEventType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(
        prefix = "avalon.console",
        name = {"enabled", "model-stream-enabled"},
        havingValue = "true"
)
public final class ConsoleModelStreamReporter implements ModelStreamEventListener {
    private final Object outputLock = new Object();
    private final Map<String, StreamState> streams = new ConcurrentHashMap<>();

    @Override
    public void onModelStreamEvent(ModelStreamEvent event) {
        if (event == null || event.callId() == null) {
            return;
        }
        synchronized (outputLock) {
            switch (event.type()) {
                case STARTED -> start(event);
                case REASONING_DELTA -> reasoning(event);
                case CONTENT_DELTA -> content(event);
                case USAGE -> {
                    // Usage is retained for metrics and intentionally omitted from the live console.
                }
                case COMPLETED -> complete(event, true);
                case FAILED -> complete(event, false);
            }
        }
    }

    private void start(ModelStreamEvent event) {
        streams.put(event.callId(), new StreamState());
        System.out.printf("%n> > > [模型流] %s | %s | 已连接，等待推理分片%n",
                label(event.playerId()), label(event.modelId()));
    }

    private void reasoning(ModelStreamEvent event) {
        StreamState state = streams.computeIfAbsent(event.callId(), ignored -> new StreamState());
        if (!state.reasoningStarted) {
            System.out.printf("> > > [供应商推理] %s | ", label(event.playerId()));
            state.reasoningStarted = true;
        }
        System.out.print(event.delta() == null ? "" : event.delta());
        System.out.flush();
    }

    private void content(ModelStreamEvent event) {
        StreamState state = streams.computeIfAbsent(event.callId(), ignored -> new StreamState());
        if (state.contentStarted) {
            return;
        }
        endReasoningLine(state);
        System.out.printf("> > > [模型输出] %s | 正在生成结构化动作%n", label(event.playerId()));
        state.contentStarted = true;
    }

    private void complete(ModelStreamEvent event, boolean success) {
        StreamState state = streams.remove(event.callId());
        endReasoningLine(state);
        if (success) {
            System.out.printf("> > > [模型流] %s | 完成，耗时=%dms，HTTP尝试=%s%n",
                    label(event.playerId()), event.elapsedMillis(),
                    event.transportAttempts() == null ? "-" : event.transportAttempts());
        } else {
            System.out.printf("> > > [模型流] %s | 中断，耗时=%dms%n",
                    label(event.playerId()), event.elapsedMillis());
        }
    }

    private void endReasoningLine(StreamState state) {
        if (state != null && state.reasoningStarted) {
            System.out.println();
            state.reasoningStarted = false;
        }
    }

    private String label(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    private static final class StreamState {
        private boolean reasoningStarted;
        private boolean contentStarted;
    }
}
