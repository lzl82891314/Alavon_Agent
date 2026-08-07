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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Single entry point for advancing a game and committing action batches. */
public final class DefaultGameCoordinator implements GameCoordinator {
    private final GameSessionService sessions;
    private final GameOrchestrator orchestrator;
    private final PlayerControllerResolver controllers;
    private final TurnContextBuilder contexts;
    private final ActionCollector collector;
    private final Map<String, Map<String, PlayerActionResult>> generatedResults = new ConcurrentHashMap<>();

    public DefaultGameCoordinator(GameSessionService sessions, GameOrchestrator orchestrator,
                                  PlayerControllerResolver controllers, TurnContextBuilder contexts,
                                  ActionCollector collector) {
        this.sessions = sessions;
        this.orchestrator = orchestrator;
        this.controllers = controllers;
        this.contexts = contexts;
        this.collector = collector;
    }

    @Override
    public synchronized AdvanceResult advance(String gameId) {
        GameRuntimeState state = sessions.require(gameId);
        if (state.status() == GameStatus.WAITING) state = orchestrator.start(gameId);
        if (state.status() != GameStatus.RUNNING) return new AdvanceResult(state, new TerminalRequirement(gameId, state.events().size(), state.phase()), null, false, "game is not running");
        Optional<ActionBatch> existing = collector.findActive(gameId);
        if (existing.isPresent()) {
            ActionBatch batch = existing.get();
            if (!batch.isComplete()) return new AdvanceResult(state, requirementFor(state), batch, false, "waiting for action submissions");
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
        Map<String, PlayerActionResult> results = new ConcurrentHashMap<>();
        batch.requiredPlayers().parallelStream().forEach(playerId -> {
            PlayerRegistration player = state.playerById(playerId);
            if (player.controllerType() == com.example.avalon.core.player.enums.PlayerControllerType.HUMAN) return;
            PlayerController controller = controllers.resolve(state, player);
            PlayerActionResult result = controller.act(contexts.build(state, player));
            results.put(playerId, result);
        });
        ActionBatch current = batch;
        for (Map.Entry<String, PlayerActionResult> entry : results.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            current = collector.submit(new ActionSubmission(current.batchId(), entry.getKey(), entry.getValue().action(),
                    current.batchId() + ":" + entry.getKey(), current.batchVersion(),
                    current.batchId() + ":" + entry.getKey(), Instant.now())).batch();
        }
        generatedResults.put(batch.batchId(), Map.copyOf(results));
        return current;
    }

    private void commit(GameRuntimeState state, ActionBatch batch) {
        if (batch.sourceGameVersion() != state.events().size()) {
            collector.invalidate(batch.batchId(), "game state changed while collecting actions");
            throw new IllegalStateException("Action batch is stale");
        }
        Map<String, PlayerActionResult> results = new LinkedHashMap<>();
        Map<String, PlayerActionResult> generated = generatedResults.remove(batch.batchId());
        batch.submissions().forEach((playerId, submission) -> results.put(playerId,
                generated != null && generated.containsKey(playerId)
                        ? generated.get(playerId)
                        : new PlayerActionResult(null, submission.action(), null, null, Map.of("batchId", batch.batchId()))));
        orchestrator.applyCollectedActions(state, results);
        collector.markCommitted(batch.batchId());
    }

    private NextRequirement requirementFor(GameRuntimeState state) {
        long version = state.events().size();
        String gameId = state.generatedGameId();
        return switch (state.phase()) {
            case DISCUSSION -> new SinglePlayerActionRequirement(gameId, version, state.phase(), state.playerByIndex(state.discussionSpeakerIndex()).playerId(), "PUBLIC_SPEECH", state.playerByIndex(state.discussionSpeakerIndex()).controllerType(), Instant.now().plusSeconds(120));
            case TEAM_PROPOSAL -> new SinglePlayerActionRequirement(gameId, version, state.phase(), state.playerBySeat(state.currentLeaderSeat()).playerId(), "TEAM_PROPOSAL", state.playerBySeat(state.currentLeaderSeat()).controllerType(), Instant.now().plusSeconds(120));
            case TEAM_VOTE -> new ParallelPlayerActionRequirement(gameId, version, state.phase(), "TEAM_VOTE", state.players().stream().map(PlayerRegistration::playerId).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)), Instant.now().plusSeconds(120));
            case MISSION_ACTION -> new ParallelPlayerActionRequirement(gameId, version, state.phase(), "MISSION_ACTION", state.currentProposalTeam().stream().map(state::playerBySeat).map(PlayerRegistration::playerId).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)), Instant.now().plusSeconds(120));
            case ASSASSINATION -> new SinglePlayerActionRequirement(gameId, version, state.phase(), state.roleAssignments().values().stream().filter(a -> a.roleId().equals(state.setup().ruleSetDefinition().assassinationRule().assassinRoleId())).findFirst().map(a -> state.playerById(a.playerId())).orElseThrow().playerId(), "ASSASSINATION", com.example.avalon.core.player.enums.PlayerControllerType.LLM, Instant.now().plusSeconds(120));
            default -> new TerminalRequirement(gameId, version, state.phase());
        };
    }
}
