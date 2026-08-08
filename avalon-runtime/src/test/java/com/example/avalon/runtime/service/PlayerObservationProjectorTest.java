package com.example.avalon.runtime.service;

import com.example.avalon.core.game.observation.FactScope;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.model.PlayerRegistration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlayerObservationProjectorTest {
    @Test
    void projectsClaimsSeparatelyAndNeverExposesPrivateEvents() {
        GameRuntimeState state = state();
        state.appendEvent("ROLE_ASSIGNED", state.phase(), "P2", Map.of("roleId", "ASSASSIN"));
        state.appendEvent("PLAYER_ACTION", state.phase(), "P2", Map.of(
                "actionType", "PUBLIC_SPEECH", "speech", "P1 很可疑", "speechAct", "ACCUSE"));
        state.appendEvent("MISSION_ACTION_CAST", state.phase(), "P2", Map.of("choice", "FAIL"));
        state.appendEvent("TEAM_PROPOSED", state.phase(), "P1", Map.of("playerIds", List.of("P1", "P3")));

        var batch = new PlayerObservationProjector().project(state, "P1",
                PlayerMemoryState.empty("g1", "P1", "LOYAL_SERVANT",
                        com.example.avalon.core.game.enums.Camp.GOOD, Instant.now()));

        assertEquals(List.of(2L, 4L), batch.events().stream().map(event -> event.sequence()).toList());
        assertEquals(FactScope.PUBLIC_CLAIM, batch.events().get(0).scope());
        assertEquals(FactScope.WORLD_FACT, batch.events().get(1).scope());
        assertFalse(batch.events().stream().anyMatch(event -> event.facts().containsKey("roleId") || event.facts().containsKey("choice")));
    }

    private GameRuntimeState state() {
        List<PlayerRegistration> players = List.of(
                new PlayerRegistration("P1", 1, "P1", PlayerControllerType.SCRIPTED),
                new PlayerRegistration("P2", 2, "P2", PlayerControllerType.SCRIPTED),
                new PlayerRegistration("P3", 3, "P3", PlayerControllerType.SCRIPTED));
        return new GameRuntimeState(new GameSetup("g1", "rules", null, "setup",
                new SetupTemplate("setup", 3, true, List.of()), 1L, Map.of(), players));
    }
}
