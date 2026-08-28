package com.example.avalon.agent.harness;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.model.AgentLoopStep;
import com.example.avalon.agent.model.AgentModelTurn;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.service.AgentTurnRequestFactory;
import com.example.avalon.agent.service.PromptBuilder;
import com.example.avalon.agent.service.ResponseParser;
import com.example.avalon.agent.service.ValidatedAgentTurn;
import com.example.avalon.agent.service.ValidationRetryPolicy;
import com.example.avalon.agent.tool.ToolCall;
import com.example.avalon.agent.tool.ToolExecutionContext;
import com.example.avalon.agent.tool.ToolExecutor;
import com.example.avalon.agent.tool.ToolPolicy;
import com.example.avalon.agent.tool.ToolResult;
import com.example.avalon.core.game.model.PlayerTurnContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded read-only cognition tool loop followed by the existing final-action validation chain. */
public final class ToolCallingAgentHarness implements AgentHarness {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallingAgentHarness.class);
    private static final int DEFAULT_MAX_ITERATIONS = 6;
    private static final long DEFAULT_TOOL_TIMEOUT_MILLIS = 5_000L;
    private static final long DEFAULT_DEADLINE_MILLIS = 60_000L;

    private final AgentTurnRequestFactory requests;
    private final PromptBuilder prompts;
    private final AgentGateway gateway;
    private final ResponseParser parser;
    private final ValidationRetryPolicy retry;
    private final ToolPolicy toolPolicy;
    private final ToolExecutor toolExecutor;

    public ToolCallingAgentHarness(AgentTurnRequestFactory requests, PromptBuilder prompts, AgentGateway gateway,
                                   ResponseParser parser, ValidationRetryPolicy retry, ToolPolicy toolPolicy,
                                   ToolExecutor toolExecutor) {
        this.requests = requests;
        this.prompts = prompts;
        this.gateway = gateway;
        this.parser = parser;
        this.retry = retry;
        this.toolPolicy = toolPolicy;
        this.toolExecutor = toolExecutor;
    }

    @Override
    public AgentHarnessType type() {
        return AgentHarnessType.TOOL_CALLING;
    }

    @Override
    public HarnessExecution execute(PlayerTurnContext context, PlayerAgentConfig profile) {
        AgentTurnRequest request = requests.create(context, profile);
        request.setPromptText(prompts.buildToolCalling(request));
        AgentGateway loopGateway = attempt -> executeLoop(attempt, profile);
        ValidatedAgentTurn validated = retry.execute(context, request, loopGateway, parser);
        return new HarnessExecution(validated.request(), validated.turnResult(), validated.action(), validated.attempts());
    }

    private AgentTurnResult executeLoop(AgentTurnRequest initial, PlayerAgentConfig profile) {
        AgentTurnRequest request = initial.copy();
        List<AgentLoopStep> steps = new ArrayList<>();
        List<Map<String, Object>> audit = new ArrayList<>();
        String runId = UUID.randomUUID().toString();
        Instant deadline = Instant.now().plusMillis(longOption(profile, "agentLoopDeadlineMillis",
                DEFAULT_DEADLINE_MILLIS));
        int maxIterations = intOption(profile, "maxAgentLoopIterations", DEFAULT_MAX_ITERATIONS);
        Duration toolTimeout = Duration.ofMillis(longOption(profile, "toolTimeoutMillis",
                DEFAULT_TOOL_TIMEOUT_MILLIS));
        ToolExecutionContext executionContext = executionContext(runId, request, deadline);
        request.setTools(toolPolicy.allowedTools(executionContext));
        int toolCallCount = 0;
        long totalTokens = 0L;

        LOGGER.info("agent_loop_start gameId={} playerId={} agentRunId={} phase={} modelId={} "
                        + "maxIterations={} toolTimeoutMs={} deadline={} tools={}",
                request.getGameId(), request.getPlayerId(), runId, request.getPhase(), request.getModelId(),
                maxIterations, toolTimeout.toMillis(), deadline,
                request.getTools().stream().map(tool -> tool.name()).toList());

        try {
            for (int iteration = 1; iteration <= maxIterations; iteration++) {
                requireBeforeDeadline(deadline);
                boundProviderTimeout(request, deadline);
                request.setLoopSteps(steps);
                LOGGER.info("agent_loop_iteration_start gameId={} playerId={} agentRunId={} iteration={} "
                                + "completedToolCalls={} accumulatedTokens={} remainingDeadlineMs={}",
                        request.getGameId(), request.getPlayerId(), runId, iteration, toolCallCount, totalTokens,
                        Math.max(0L, Duration.between(Instant.now(), deadline).toMillis()));
                AgentModelTurn turn = gateway.nextTurn(request);
                requireBeforeDeadline(deadline);
                long turnTokens = tokenCount(turn);
                totalTokens += turnTokens;
                LOGGER.info("agent_loop_model_turn gameId={} playerId={} agentRunId={} iteration={} outcome={} "
                                + "requestedToolCalls={} inputTokens={} outputTokens={} turnTokens={} totalTokens={}",
                        request.getGameId(), request.getPlayerId(), runId, iteration,
                        turn.requiresTools() ? "TOOL_CALLS" : "FINAL_OUTPUT", turn.toolCalls().size(),
                        turn.modelMetadata().getInputTokens(), turn.modelMetadata().getOutputTokens(),
                        turnTokens, totalTokens);
                if (!turn.requiresTools()) {
                    AgentTurnResult result = turn.finalResult();
                    result.getModelMetadata().getAttributes().put("agentRunId", runId);
                    result.getModelMetadata().getAttributes().put("agentLoopIterations", iteration);
                    result.getModelMetadata().getAttributes().put("agentToolCalls", toolCallCount);
                    result.getModelMetadata().getAttributes().put("agentToolAudit", List.copyOf(audit));
                    LOGGER.info("agent_loop_complete gameId={} playerId={} agentRunId={} iterations={} "
                                    + "toolCalls={} totalTokens={} actionJson={} publicSpeech={} privateThought={}",
                            request.getGameId(), request.getPlayerId(), runId, iteration, toolCallCount, totalTokens,
                            result.getActionJson(), result.getPublicSpeech(), result.getPrivateThought());
                    return result;
                }
                List<ToolResult> results = new ArrayList<>();
                for (ToolCall call : turn.toolCalls()) {
                    requireBeforeDeadline(deadline);
                    LOGGER.info("agent_tool_call gameId={} playerId={} agentRunId={} iteration={} callId={} "
                                    + "toolName={} arguments={}",
                            request.getGameId(), request.getPlayerId(), runId, iteration, call.callId(),
                            call.toolName(), call.arguments());
                    ToolResult result = toolExecutor.execute(executionContext, call, toolTimeout);
                    LOGGER.info("agent_tool_result gameId={} playerId={} agentRunId={} iteration={} callId={} "
                                    + "toolName={} status={} durationMs={} sourceSequences={} content={} "
                                    + "errorType={} errorMessage={}",
                            request.getGameId(), request.getPlayerId(), runId, iteration, result.callId(),
                            result.toolName(), result.status(), result.durationMillis(), result.sourceSequences(),
                            result.content(), result.errorType(), result.errorMessage());
                    results.add(result);
                    audit.add(auditEntry(iteration, request, call, result));
                    toolCallCount++;
                }
                steps.add(new AgentLoopStep(turn.toolCalls(), results));
            }
            throw new AgentLoopLimitException("ITERATION_LIMIT_EXCEEDED",
                    "Agent loop exceeded iteration limit: " + maxIterations);
        } catch (RuntimeException exception) {
            LOGGER.info("agent_loop_failed gameId={} playerId={} agentRunId={} completedIterations={} "
                            + "toolCalls={} totalTokens={} errorType={} errorMessage={}",
                    request.getGameId(), request.getPlayerId(), runId, steps.size(), toolCallCount, totalTokens,
                    exception.getClass().getSimpleName(), exception.getMessage());
            throw exception;
        }
    }

    private ToolExecutionContext executionContext(String runId, AgentTurnRequest request, Instant deadline) {
        Object agentInstance = request.getMemory().get("agentInstanceId");
        String agentInstanceId = agentInstance == null
                ? request.getPlayerId() + ":primary"
                : String.valueOf(agentInstance);
        return new ToolExecutionContext(runId, request.getGameId(), request.getPlayerId(), agentInstanceId,
                request, deadline);
    }

    private Map<String, Object> auditEntry(int iteration, AgentTurnRequest request,
                                           ToolCall call, ToolResult result) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("iteration", iteration);
        entry.put("callId", call.callId());
        entry.put("toolName", call.toolName());
        entry.put("arguments", call.arguments());
        entry.put("argumentKeys", call.arguments().keySet().stream().sorted().toList());
        entry.put("observationFromSequence", request.getObservationFromSequence());
        entry.put("observationToSequence", request.getObservationToSequence());
        entry.put("status", result.status().name());
        entry.put("durationMillis", result.durationMillis());
        entry.put("sourceSequences", result.sourceSequences());
        entry.put("resultSummary", Map.of(
                "contentKeys", result.content().keySet().stream().sorted().toList(),
                "contentFieldCount", result.content().size(),
                "content", result.content()));
        if (result.errorType() != null) entry.put("errorType", result.errorType());
        return Map.copyOf(entry);
    }

    private long tokenCount(AgentModelTurn turn) {
        Long input = turn.modelMetadata().getInputTokens();
        Long output = turn.modelMetadata().getOutputTokens();
        return (input == null ? 0L : input) + (output == null ? 0L : output);
    }

    private void requireBeforeDeadline(Instant deadline) {
        if (!Instant.now().isBefore(deadline)) {
            throw new AgentLoopLimitException("AGENT_LOOP_DEADLINE_EXCEEDED", "Agent loop deadline exceeded");
        }
    }

    private void boundProviderTimeout(AgentTurnRequest request, Instant deadline) {
        long remaining = Math.max(1L, Duration.between(Instant.now(), deadline).toMillis());
        Map<String, Object> options = new LinkedHashMap<>(request.getProviderOptions());
        Object configured = options.get("timeoutMillis");
        long configuredMillis = configured instanceof Number number ? number.longValue()
                : parseLong(configured, remaining);
        options.put("timeoutMillis", Math.max(1L, Math.min(remaining, configuredMillis)));
        request.setProviderOptions(options);
    }

    private long parseLong(Object value, long fallback) {
        if (value == null) return fallback;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int intOption(PlayerAgentConfig profile, String key, int fallback) {
        Object value = profile == null ? null : profile.getCognition().get(key);
        int parsed = value instanceof Number number ? number.intValue() : fallback;
        return parsed > 0 ? parsed : fallback;
    }

    private long longOption(PlayerAgentConfig profile, String key, long fallback) {
        Object value = profile == null ? null : profile.getCognition().get(key);
        long parsed = value instanceof Number number ? number.longValue() : fallback;
        return parsed > 0L ? parsed : fallback;
    }

}
