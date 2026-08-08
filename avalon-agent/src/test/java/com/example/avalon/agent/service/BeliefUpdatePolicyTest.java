package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.MemoryUpdate;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.DiscussionStage;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.enums.PlayerConnectionState;
import com.example.avalon.core.game.model.AllowedActionSet;
import com.example.avalon.core.game.model.DiscussionTurnDirective;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.PublicGameSnapshot;
import com.example.avalon.core.game.model.PublicPlayerSummary;
import com.example.avalon.core.game.observation.FactScope;
import com.example.avalon.core.game.observation.ObservedGameEvent;
import com.example.avalon.core.game.observation.PlayerObservationBatch;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.player.memory.VisiblePlayerInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BeliefUpdatePolicyTest {
    @Test
    void rejectsLargeBeliefChangeWithoutEvidence() {
        assertThrows(AgentTurnExecutionException.class,
                () -> execute(context(memoryWithPrior(0.5d), List.of()), result(0.9d, List.of())));
    }

    @Test
    void rejectsExcessiveBeliefChangeEvenWithVisibleEvidence() {
        assertThrows(AgentTurnExecutionException.class,
                () -> execute(context(memoryWithPrior(0.5d), visibleEvent()), result(0.9d, List.of(5L))));
    }

    @Test
    void acceptsBoundedBeliefRevisionWithVisibleEvidence() {
        assertDoesNotThrow(
                () -> execute(context(memoryWithPrior(0.5d), visibleEvent()),
                        result(0.7d, List.of(5L), Map.of("P2", List.of(5L)))));
    }

    @Test
    void rejectsSharedEvidenceThatDoesNotConcernEveryChangedPlayer() {
        PlayerMemoryState memory = memoryWithPriors(Map.of("P2", 0.5d, "P3", 0.5d));
        AgentTurnResult result = result(Map.of("P2", 0.7d, "P3", 0.7d), List.of(5L),
                Map.of("P2", List.of(5L), "P3", List.of(5L)));

        assertThrows(AgentTurnExecutionException.class,
                () -> execute(context(memory, visibleEvent()), result));
    }

    @Test
    void rejectsInvisiblePerPlayerEvidenceBinding() {
        assertThrows(AgentTurnExecutionException.class,
                () -> execute(context(memoryWithPrior(0.5d), visibleEvent()),
                        result(0.7d, List.of(5L), Map.of("P2", List.of(999L)))));
    }

    @Test
    void acceptsPrivateCampCertaintyWithoutPublicEvidence() {
        PlayerTurnContext context = context(PlayerMemoryState.empty(
                "g1", "P1", "MERLIN", Camp.GOOD, Instant.now()), List.of(),
                List.of(new VisiblePlayerInfo("P2", 2, "P2", null, Camp.EVIL,
                        List.of("ASSASSIN", "MORGANA"))));

        assertDoesNotThrow(() -> execute(context, result(1.0d, List.of())));
    }

    private void execute(PlayerTurnContext context, AgentTurnResult result) {
        AgentGateway gateway = request -> result;
        AgentTurnRequest request = new AgentTurnRequest();
        request.setAllowedActions(List.of("PUBLIC_SPEECH"));
        new ValidationRetryPolicy().execute(context, request, gateway, new ResponseParser());
    }

    private AgentTurnResult result(double p2Probability, List<Long> evidence) {
        return result(p2Probability, evidence, Map.of());
    }

    private AgentTurnResult result(double p2Probability, List<Long> evidence,
                                   Map<String, List<Long>> bindings) {
        return result(Map.of("P2", p2Probability), evidence, bindings);
    }

    private AgentTurnResult result(Map<String, Double> beliefs, List<Long> evidence,
                                   Map<String, List<Long>> bindings) {
        AgentTurnResult result = new AgentTurnResult();
        result.setPublicSpeech("我根据当前可见证据调整判断。");
        result.setActionJson("{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"我根据当前可见证据调整判断。\",\"speechAct\":\"STATE_OPINION\",\"mentions\":[],\"replyToEventSequences\":[]}");
        MemoryUpdate update = new MemoryUpdate();
        update.setRoleBeliefs(beliefs);
        update.setEvidenceReferences(evidence);
        update.setBeliefEvidenceReferences(bindings);
        result.setMemoryUpdate(update);
        return result;
    }

    private PlayerMemoryState memoryWithPrior(double prior) {
        return memoryWithPriors(Map.of("P2", prior));
    }

    private PlayerMemoryState memoryWithPriors(Map<String, Double> priors) {
        return new PlayerMemoryState(
                "g1", "P1", 1L, "LOYAL_SERVANT", Camp.GOOD,
                Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                priors, Map.of(), Map.of(), Map.of(), 0L, "P1:primary", "NEUTRAL", null, Instant.now());
    }

    private PlayerTurnContext context(PlayerMemoryState memory, List<ObservedGameEvent> observations) {
        return context(memory, observations, List.of());
    }

    private PlayerTurnContext context(PlayerMemoryState memory,
                                      List<ObservedGameEvent> observations,
                                      List<VisiblePlayerInfo> visiblePlayers) {
        PublicGameSnapshot snapshot = new PublicGameSnapshot(
                "g1", GameStatus.RUNNING, GamePhase.DISCUSSION, 1, 0, 0, 0, 1,
                DiscussionStage.OPENING_STATEMENTS, "P1", 5L, List.of(), null, null,
                List.of(player("P1", 1), player("P2", 2), player("P3", 3)), Instant.now());
        return new PlayerTurnContext(
                "g1", 1, "DISCUSSION", "P1", 1, memory.roleId(), snapshot,
                new PlayerPrivateView("g1", "P1", 1, memory.roleId(), memory.camp(),
                        new PlayerPrivateKnowledge(visiblePlayers, List.of()), List.of()),
                memory, new PlayerObservationBatch("g1", "P1", "P1:primary", 0, 5, observations),
                new DiscussionTurnDirective("OPENING_STATEMENTS", List.of("STATE_OPINION"), null, null, 1),
                new AllowedActionSet("g1", "P1", 1, Set.of(PlayerActionType.PUBLIC_SPEECH)),
                null, null, "rules");
    }

    private List<ObservedGameEvent> visibleEvent() {
        return List.of(new ObservedGameEvent(5L, "PUBLIC_SPEECH", "P2", FactScope.PUBLIC_CLAIM,
                Map.of("claim", "team is safe"), "team is safe", "STATE_OPINION",
                List.of(), List.of(), Instant.now()));
    }

    private PublicPlayerSummary player(String playerId, int seatNo) {
        return new PublicPlayerSummary("g1", playerId, seatNo, playerId,
                PlayerControllerType.LLM, PlayerConnectionState.CONNECTED);
    }
}
