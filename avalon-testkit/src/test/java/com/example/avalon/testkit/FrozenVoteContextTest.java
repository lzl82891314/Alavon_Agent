package com.example.avalon.testkit;

import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.game.model.PlayerActionResult;
import com.example.avalon.core.game.model.TeamVoteAction;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.runtime.controller.PlayerControllerResolver;
import com.example.avalon.runtime.engine.ConfigDrivenGameRuleEngine;
import com.example.avalon.runtime.engine.RoleAssignmentService;
import com.example.avalon.runtime.engine.VisibilityService;
import com.example.avalon.runtime.orchestrator.GameOrchestrator;
import com.example.avalon.runtime.service.GameSessionService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FrozenVoteContextTest {
    @Test
    void directStepFreezesEveryVotersContextBeforeAnyVoteIsRecorded() {
        GameSessionService sessions = new GameSessionService();
        Map<String, Long> observedThrough = new LinkedHashMap<>();
        Map<String, List<String>> observedTypes = new LinkedHashMap<>();
        PlayerControllerResolver controllers = new PlayerControllerResolver()
                .registerFactory(PlayerControllerType.SCRIPTED, (state, player) -> context -> {
                    observedThrough.put(player.playerId(), context.observations().toSequenceInclusive());
                    observedTypes.put(player.playerId(), context.observations().events().stream()
                            .map(event -> event.eventType()).toList());
                    return new PlayerActionResult(null, new TeamVoteAction(VoteChoice.APPROVE),
                            null, null, Map.of());
                });
        GameOrchestrator orchestrator = new GameOrchestrator(
                sessions,
                new ConfigDrivenGameRuleEngine(),
                new RoleAssignmentService(),
                new VisibilityService(),
                controllers);
        var state = orchestrator.createGame(ScriptedAvalonFixture.classicFivePlayerSetup(17L));
        orchestrator.start(state.generatedGameId());
        state.phase(GamePhase.TEAM_VOTE);
        state.clearProposalState();
        state.addCurrentProposalSeat(1);
        state.addCurrentProposalSeat(2);

        orchestrator.step(state.generatedGameId());

        assertEquals(5, observedThrough.size());
        assertEquals(1, observedThrough.values().stream().distinct().count());
        observedTypes.values().forEach(types -> {
            assertFalse(types.contains("TEAM_VOTE_CAST"));
            assertFalse(types.contains("TEAM_VOTES_REVEALED"));
        });
        assertEquals(1L, state.events().stream()
                .filter(event -> "TEAM_VOTES_REVEALED".equals(event.type())).count());
        assertEquals(0L, state.events().stream()
                .filter(event -> "TEAM_VOTE_CAST".equals(event.type())).count());
    }
}
