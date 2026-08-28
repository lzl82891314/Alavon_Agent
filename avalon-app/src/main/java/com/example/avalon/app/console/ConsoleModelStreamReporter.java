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
    private static final String PUBLIC_SPEECH_MARKER = "\"publicSpeech\":\"";
    private static final String PRIVATE_THOUGHT_MARKER = "\"privateThought\":\"";
    private final Object outputLock = new Object();
    private final Map<String, StreamState> streams = new ConcurrentHashMap<>();
    private volatile ConsoleLogLevel logLevel = ConsoleLogLevel.INFO;

    public void setLogLevel(ConsoleLogLevel logLevel) {
        this.logLevel = logLevel == null ? ConsoleLogLevel.INFO : logLevel;
    }

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
        if (logLevel != ConsoleLogLevel.INFO) {
            System.out.printf("%n> > > [模型流] %s | %s | 已连接，等待推理分片%n",
                    label(event.playerId()), label(event.modelId()));
        }
    }

    private void reasoning(ModelStreamEvent event) {
        if (logLevel == ConsoleLogLevel.INFO) {
            return;
        }
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
        if (logLevel == ConsoleLogLevel.DEBUG || logLevel == ConsoleLogLevel.TRACE) {
            endReasoningLine(state);
            System.out.print(event.delta() == null ? "" : event.delta());
            System.out.flush();
        }
        if (logLevel == ConsoleLogLevel.INFO) {
            state.publicSpeech.accept(event.delta(), event.playerId());
            state.privateThought.accept(event.delta(), event.playerId());
            state.actionType.accept(event.delta(), event.playerId());
            state.vote.accept(event.delta(), event.playerId());
            state.choice.accept(event.delta(), event.playerId());
            state.target.accept(event.delta(), event.playerId());
            state.team.accept(event.delta(), event.playerId());
        }
    }

    private void complete(ModelStreamEvent event, boolean success) {
        StreamState state = streams.remove(event.callId());
        endReasoningLine(state);
        if (state != null) {
            if (logLevel == ConsoleLogLevel.INFO) {
                state.publicSpeech.finish(event.playerId());
                state.privateThought.finish(event.playerId());
                state.actionType.finish(event.playerId());
                state.vote.finish(event.playerId());
                state.choice.finish(event.playerId());
                state.target.finish(event.playerId());
                state.team.finish(event.playerId());
            }
        }
        if (logLevel != ConsoleLogLevel.INFO && success) {
            System.out.printf("> > > [模型流] %s | 完成，耗时=%dms，HTTP尝试=%s%n",
                    label(event.playerId()), event.elapsedMillis(),
                    event.transportAttempts() == null ? "-" : event.transportAttempts());
        } else if (logLevel != ConsoleLogLevel.INFO) {
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
        private final JsonFieldStreamer publicSpeech = new JsonFieldStreamer(
                PUBLIC_SPEECH_MARKER, "公开发言流");
        private final JsonFieldStreamer privateThought = new JsonFieldStreamer(
                PRIVATE_THOUGHT_MARKER, "私有思考流");
        private final JsonFieldStreamer actionType = new JsonFieldStreamer(
                "\"actionType\":\"", "动作类型");
        private final JsonFieldStreamer vote = new JsonFieldStreamer(
                "\"vote\":\"", "投票");
        private final JsonFieldStreamer choice = new JsonFieldStreamer(
                "\"choice\":\"", "任务选择");
        private final JsonFieldStreamer target = new JsonFieldStreamer(
                "\"targetPlayerId\":\"", "刺杀目标");
        private final JsonFieldStreamer team = new JsonFieldStreamer(
                "\"selectedPlayerIds\":[", "提议队伍", true);
    }

    /** Streams one JSON string field without exposing the incomplete action object. */
    private static final class JsonFieldStreamer {
        private final String marker;
        private final String title;
        private final StringBuilder search = new StringBuilder();
        private final StringBuilder sentence = new StringBuilder();
        private boolean found;
        private boolean escaped;
        private boolean finished;
        private boolean printed;
        private final boolean array;

        private JsonFieldStreamer(String marker, String title) {
            this(marker, title, false);
        }

        private JsonFieldStreamer(String marker, String title, boolean array) {
            this.marker = marker;
            this.title = title;
            this.array = array;
        }

        private void accept(String delta, String playerId) {
            if (delta == null || delta.isEmpty() || finished) {
                return;
            }
            for (int i = 0; i < delta.length() && !finished; i++) {
                char current = delta.charAt(i);
                if (!found) {
                    search.append(current);
                    if (search.toString().endsWith(marker)) {
                        found = true;
                        search.setLength(0);
                    } else if (search.length() >= marker.length()) {
                        search.delete(0, search.length() - marker.length() + 1);
                    }
                    continue;
                }
                if (escaped) {
                    sentence.append(current == 'n' ? '\n' : current);
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (array && current == ']') {
                    finished = true;
                    flush(playerId);
                } else if (array && current == '"') {
                    // Ignore JSON array string delimiters.
                } else if (current == '"') {
                    finished = true;
                    flush(playerId);
                } else {
                    sentence.append(current);
                    if (isSentenceEnd(current)) {
                        flush(playerId);
                    }
                }
            }
        }

        private void finish(String playerId) {
            if (found) {
                flush(playerId);
                if (printed) {
                    System.out.println();
                }
            }
        }

        private void flush(String playerId) {
            String value = sentence.toString().trim();
            sentence.setLength(0);
            if (value.isEmpty()) {
                return;
            }
            if (!printed) {
                System.out.printf("> > > [%s] %s | ", title, labelValue(playerId));
                printed = true;
            }
            System.out.print(value);
            System.out.flush();
            if (value.endsWith("。") || value.endsWith("！") || value.endsWith("？")
                    || value.endsWith("!") || value.endsWith("?") || value.contains("\n")) {
                System.out.println();
            }
        }

        private boolean isSentenceEnd(char value) {
            return value == '。' || value == '！' || value == '？'
                    || value == '!' || value == '?' || value == '\n';
        }

        private String labelValue(String value) {
            return value == null || value.isBlank() ? "未知" : value;
        }
    }
}
