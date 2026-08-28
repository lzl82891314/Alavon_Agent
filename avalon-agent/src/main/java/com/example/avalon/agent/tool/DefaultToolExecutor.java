package com.example.avalon.agent.tool;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public final class DefaultToolExecutor implements ToolExecutor, AutoCloseable {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final Set<String> RESERVED_ARGUMENTS = Set.of(
            "gameId", "ownerPlayerId", "observerPlayerId", "agentInstanceId", "roleId", "privateView");
    private final ToolRegistry registry;
    private final ToolPolicy policy;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public DefaultToolExecutor(ToolRegistry registry, ToolPolicy policy) {
        this.registry = registry;
        this.policy = policy;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, ToolCall call, Duration timeout) {
        long started = System.nanoTime();
        String forbidden = call.arguments().keySet().stream().filter(RESERVED_ARGUMENTS::contains).findFirst().orElse(null);
        if (forbidden != null) return rejected(call, "PERMISSION_ARGUMENT_REJECTED",
                "Permission identity is supplied by the host, not tool arguments: " + forbidden, started);
        AgentTool tool = registry.find(call.toolName()).orElse(null);
        if (tool == null) return rejected(call, "UNKNOWN_TOOL", "Unknown tool: " + call.toolName(), started);
        if (!tool.descriptor().readOnly()) return rejected(call, "MUTATING_TOOL_REJECTED",
                "Only read-only cognition tools may execute", started);
        if (!policy.allows(context, call.toolName())) return rejected(call, "TOOL_NOT_ALLOWED",
                "Tool is not allowed for the current phase and action set", started);
        Duration effectiveTimeout = positive(timeout);
        if (!Instant.MAX.equals(context.deadline())) {
            Duration remaining = Duration.between(Instant.now(), context.deadline());
            effectiveTimeout = min(effectiveTimeout, positive(remaining));
        }
        Future<ToolResult> future = executor.submit(() -> tool.execute(context, call));
        try {
            return future.get(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS).withDuration(elapsed(started));
        } catch (TimeoutException exception) {
            future.cancel(true);
            return new ToolResult(call.callId(), call.toolName(), ToolExecutionStatus.TIMED_OUT, null, null,
                    "TOOL_TIMEOUT", "Tool execution exceeded its deadline", elapsed(started));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ToolResult(call.callId(), call.toolName(), ToolExecutionStatus.FAILED, null, null,
                    "TOOL_INTERRUPTED", "Tool execution was interrupted", elapsed(started));
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            String type = cause instanceof IllegalArgumentException ? "INVALID_TOOL_ARGUMENTS" : "TOOL_EXECUTION_FAILED";
            return new ToolResult(call.callId(), call.toolName(), ToolExecutionStatus.FAILED, null, null,
                    type, cause == null ? exception.getMessage() : cause.getMessage(), elapsed(started));
        }
    }

    private ToolResult rejected(ToolCall call, String type, String message, long started) {
        return new ToolResult(call.callId(), call.toolName(), ToolExecutionStatus.REJECTED, null, null,
                type, message, elapsed(started));
    }

    private Duration positive(Duration value) {
        if (value == null) return DEFAULT_TIMEOUT;
        if (value.isZero() || value.isNegative()) return Duration.ofMillis(1);
        return value;
    }

    private Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    @Override
    public void close() {
        executor.close();
    }
}
