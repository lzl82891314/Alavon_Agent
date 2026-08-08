package com.example.avalon.agent.service;

import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.MemoryUpdate;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.model.AllowedActionSet;
import com.example.avalon.core.game.model.DiscussionTurnDirective;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.observation.FactScope;
import com.example.avalon.core.game.observation.ObservedGameEvent;
import com.example.avalon.core.game.observation.PlayerObservationBatch;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceBoundaryTest {
    @Test
    void rejectsEvidenceThatWasNeverVisibleToTheAgent() {
        AgentGateway gateway = request -> invalidResult();
        AgentTurnRequest request = new AgentTurnRequest();
        request.setAllowedActions(List.of("PUBLIC_SPEECH"));

        assertThrows(AgentTurnExecutionException.class, () -> new ValidationRetryPolicy().execute(
                context(), request, gateway, new ResponseParser()));
    }

    private AgentTurnResult invalidResult() {
        AgentTurnResult result = new AgentTurnResult();
        result.setPublicSpeech("我只依据公开证据判断。");
        result.setActionJson("{\"actionType\":\"PUBLIC_SPEECH\",\"speechText\":\"我只依据公开证据判断。\",\"speechAct\":\"STATE_OPINION\",\"mentions\":[],\"replyToEventSequences\":[]}");
        MemoryUpdate update = new MemoryUpdate();
        update.setEvidenceReferences(List.of(999L));
        update.setBeliefEvidenceReferences(Map.of("P2", List.of(999L)));
        update.setRoleBeliefs(Map.of("P2", 0.7d));
        result.setMemoryUpdate(update);
        return result;
    }

    private PlayerTurnContext context() {
        PlayerMemoryState memory = PlayerMemoryState.empty("g1", "P1", "LOYAL_SERVANT", Camp.GOOD, Instant.now());
        PlayerObservationBatch observations = new PlayerObservationBatch("g1", "P1", "P1:primary", 0, 5,
                List.of(new ObservedGameEvent(5, "TEAM_PROPOSED", "P2", FactScope.WORLD_FACT,
                        Map.of("playerIds", List.of("P2", "P3")), null, null, List.of(), List.of(), Instant.now())));
        return new PlayerTurnContext("g1", 1, "DISCUSSION", "P1", 1, "LOYAL_SERVANT", null,
                new PlayerPrivateView("g1", "P1", 1, "LOYAL_SERVANT", Camp.GOOD,
                        new PlayerPrivateKnowledge(List.of(), List.of()), List.of()),
                memory, observations,
                new DiscussionTurnDirective("OPENING_STATEMENTS", List.of("STATE_OPINION"), null, null, 1),
                new AllowedActionSet("g1", "P1", 1, Set.of(PlayerActionType.PUBLIC_SPEECH)), null, null, "rules");
    }
}
