package com.example.avalon.runtime.coordination;

import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.model.PlayerActionResult;
import com.example.avalon.core.player.controller.PlayerController;
import com.example.avalon.runtime.controller.PlayerControllerResolver;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.PlayerRegistration;
import com.example.avalon.runtime.orchestrator.GameOrchestrator;
import com.example.avalon.runtime.service.GameSessionService;
import com.example.avalon.runtime.service.TurnContextBuilder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

/** Single entry point for advancing a game and committing action batches. */
public final class DefaultGameCoordinator implements GameCoordinator {
    private final GameSessionService sessions;
    private final GameOrchestrator orchestrator;
    private final PlayerControllerResolver controllers;
    private final TurnContextBuilder contexts;
    private final ActionCollector collector;
    private final Executor agentExecutor;

    public DefaultGameCoordinator(GameSessionService sessions, GameOrchestrator orchestrator,
                                  PlayerControllerResolver controllers, TurnContextBuilder contexts,
                                  ActionCollector collector) {
        this(sessions, orchestrator, controllers, contexts, collector, Runnable::run);
    }

    public DefaultGameCoordinator(GameSessionService sessions, GameOrchestrator orchestrator,
                                  PlayerControllerResolver controllers, TurnContextBuilder contexts,
                                  ActionCollector collector, Executor agentExecutor) {
        this.sessions = sessions;
        this.orchestrator = orchestrator;
        this.controllers = controllers;
        this.contexts = contexts;
        this.collector = collector;
        this.agentExecutor = agentExecutor == null ? Runnable::run : agentExecutor;
    }

    @Override
    public synchronized AdvanceResult advance(String gameId) {
        GameRuntimeState state = sessions.require(gameId);
        if (state.status() == GameStatus.WAITING) state = orchestrator.start(gameId);
        if (state.status() != GameStatus.RUNNING) return new AdvanceResult(state, new TerminalRequirement(gameId, state.events().size(), state.phase()), null, false, "game is not running");
        Optional<ActionBatch> existing = collector.findActive(gameId);
        if (existing.isPresent()) {
            ActionBatch batch = existing.get();
            if (!batch.isComplete()) {
                batch = dispatchAutomatic(state, batch);
                if (state.status() != GameStatus.RUNNING) {
                    return new AdvanceResult(state, requirementFor(state), batch, true,
                            "game paused after controller failure");
                }
                if (!batch.isComplete()) {
                    return new AdvanceResult(state, requirementFor(state), batch, false,
                            "waiting for action submissions");
                }
            }
            commit(state, batch);
            return new AdvanceResult(state, requirementFor(state), batch, true, "action batch committed");
        }
        if (state.phase() == GamePhase.MISSION_RESOLUTION) {
            orchestrator.step(gameId);
            return new AdvanceResult(state, requirementFor(state), null, true, "automatic transition applied");
        }
        NextRequirement requirement = requirementFor(state);
        if (requirement instanceof TerminalRequirement) return new AdvanceResult(state, requirement, null, false, "game ended");
        ActionBatch batch = collector.open(requirement);
        batch = dispatchAutomatic(state, batch);
        if (state.status() != GameStatus.RUNNING) {
            return new AdvanceResult(state, requirementFor(state), batch, true,
                    "game paused after controller failure");
        }
        if (batch.isComplete()) {
            commit(state, batch);
            return new AdvanceResult(state, requirementFor(state), batch, true, "action batch committed");
        }
        return new AdvanceResult(state, requirement, batch, true, "waiting for human submissions");
    }

    @Override
    public AdvanceResult runUntilBlocked(String gameId, int budget) {
        AdvanceResult result = null;
        int remaining = budget <= 0 ? 500 : budget;
        while (remaining-- > 0) {
            result = advance(gameId);
            if (result.blocked() || result.state().status() != GameStatus.RUNNING) return result;
        }
        throw new IllegalStateException("Coordinator budget exhausted");
    }

    @Override public SubmissionResult submit(ActionSubmission submission) { return collector.submit(submission); }
    @Override public Optional<ActionBatch> findActiveBatch(String gameId) { return collector.findActive(gameId); }

    private ActionBatch dispatchAutomatic(GameRuntimeState state, ActionBatch batch) {
        Map<String, FrozenControllerInvocation> frozenInvocations = new LinkedHashMap<>();
        for (String playerId : batch.missingPlayers().stream().sorted().toList()) {
            PlayerRegistration player = state.playerById(playerId);
            if (player.controllerType() == com.example.avalon.core.player.enums.PlayerControllerType.HUMAN) continue;
            PlayerController controller = controllers.resolve(state, player);
            frozenInvocations.put(playerId, new FrozenControllerInvocation(playerId, controller, contexts.build(state, player)));
        }
        BlockingQueue<CompletedAction> completedActions = new LinkedBlockingQueue<>();
        frozenInvocations.values().forEach(invocation -> CompletableFuture.supplyAsync(
                        () -> new GeneratedAction(invocation.playerId(), invocation.controller().act(invocation.context())),
                        agentExecutor)
                .whenComplete((generated, failure) -> completedActions.add(
                        new CompletedAction(invocation.playerId(), generated, failure))));
        ActionBatch current = batch;
        FailedAction firstLlmFailure = null;
        RuntimeException firstUnexpectedFailure = null;
        for (int completed = 0; completed < frozenInvocations.size(); completed++) {
            CompletedAction completedAction;
            try {
                completedAction = completedActions.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for automatic controller", exception);
            }
            if (completedAction.failure() == null) {
                GeneratedAction generated = completedAction.generated();
                current = collector.submit(new ActionSubmission(current.batchId(), generated.playerId(), generated.result().action(),
                        current.batchId() + ":" + generated.playerId(), current.batchVersion(),
                        current.batchId() + ":" + generated.playerId(), Instant.now(), generated.result())).batch();
            } else {
                Throwable cause = unwrapCompletionFailure(completedAction.failure());
                if (cause instanceof com.example.avalon.core.player.controller.PlayerActionGenerationException failure
                        && state.playerById(completedAction.playerId()).controllerType()
                        == com.example.avalon.core.player.enums.PlayerControllerType.LLM) {
                    if (firstLlmFailure == null) {
                        firstLlmFailure = new FailedAction(completedAction.playerId(), failure);
                    }
                } else if (firstUnexpectedFailure == null) {
                    firstUnexpectedFailure = cause instanceof RuntimeException runtimeException
                            ? runtimeException
                            : new IllegalStateException("Automatic controller failed", cause);
                }
            }
        }
        if (firstUnexpectedFailure != null) {
            throw firstUnexpectedFailure;
        }
        if (firstLlmFailure != null) {
            orchestrator.pauseForLlmFailure(
                    state,
                    state.playerById(firstLlmFailure.playerId()),
                    firstLlmFailure.failure());
        }
        return current;
    }

    private Throwable unwrapCompletionFailure(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
    }

    private record FrozenControllerInvocation(String playerId, PlayerController controller,
                                              com.example.avalon.core.game.model.PlayerTurnContext context) { }

    private record GeneratedAction(String playerId, PlayerActionResult result) { }

    private record CompletedAction(String playerId, GeneratedAction generated, Throwable failure) { }

    private record FailedAction(
            String playerId,
            com.example.avalon.core.player.controller.PlayerActionGenerationException failure
    ) { }

    private void commit(GameRuntimeState state, ActionBatch batch) {
        if (batch.sourceGameVersion() != state.events().size()) {
            collector.invalidate(batch.batchId(), "game state changed while collecting actions");
            throw new IllegalStateException("Action batch is stale");
        }
        Map<String, PlayerActionResult> results = new LinkedHashMap<>();
        batch.submissions().forEach((playerId, submission) -> results.put(playerId,
                submission.actionResult() != null
                        ? submission.actionResult()
                        : new PlayerActionResult(null, submission.action(), null, null, Map.of("batchId", batch.batchId()))));
        orchestrator.applyCollectedActions(state, results);
        collector.markCommitted(batch.batchId());
    }

    private NextRequirement requirementFor(GameRuntimeState state) {
        long version = state.events().size();
        String gameId = state.generatedGameId();
        return switch (state.phase()) {
            case DISCUSSION -> new SinglePlayerActionRequirement(gameId, version, state.phase(), state.currentDiscussionSpeaker().playerId(), "PUBLIC_SPEECH", state.currentDiscussionSpeaker().controllerType(), Instant.now().plusSeconds(120));
            case TEAM_PROPOSAL -> new SinglePlayerActionRequirement(gameId, version, state.phase(), state.playerBySeat(state.currentLeaderSeat()).playerId(), "TEAM_PROPOSAL", state.playerBySeat(state.currentLeaderSeat()).controllerType(), Instant.now().plusSeconds(120));
            case TEAM_VOTE -> new ParallelPlayerActionRequirement(gameId, version, state.phase(), "TEAM_VOTE", state.players().stream().map(PlayerRegistration::playerId).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)), Instant.now().plusSeconds(120));
            case MISSION_ACTION -> new ParallelPlayerActionRequirement(gameId, version, state.phase(), "MISSION_ACTION", state.currentProposalTeam().stream().map(state::playerBySeat).map(PlayerRegistration::playerId).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)), Instant.now().plusSeconds(120));
            case ASSASSINATION -> {
                PlayerRegistration assassin = state.roleAssignments().values().stream()
                        .filter(a -> a.roleId().equals(state.setup().ruleSetDefinition().assassinationRule().assassinRoleId()))
                        .findFirst().map(a -> state.playerById(a.playerId())).orElseThrow();
                yield new SinglePlayerActionRequirement(gameId, version, state.phase(), assassin.playerId(),
                        "ASSASSINATION", assassin.controllerType(), Instant.now().plusSeconds(120));
            }
            default -> new TerminalRequirement(gameId, version, state.phase());
        };
    }
}
