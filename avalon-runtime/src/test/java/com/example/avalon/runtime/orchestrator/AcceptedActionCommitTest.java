package com.example.avalon.runtime.orchestrator;

import com.example.avalon.core.common.exception.GameRuleViolationException;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.model.MissionAction;
import com.example.avalon.core.game.model.PlayerActionResult;
import com.example.avalon.core.game.model.PublicSpeechAction;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.player.memory.MemoryUpdate;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.model.PlayerRegistration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceptedActionCommitTest {
    @Test
    void acceptedActionCommitsOnlyTheActingPlayersMemory() {
        GameRuntimeState state = state();
        state.phase(GamePhase.DISCUSSION);

        new GameOrchestrator().applyCollectedActions(state, Map.of("P1", new PlayerActionResult(
                "有证据再下判断。",
                new PublicSpeechAction("有证据再下判断。", "STATE_OPINION", List.of(), List.of()),
                null,
                memoryUpdate("P1 opening", 0L),
                Map.of())));

        assertEquals("P1 opening", state.memoryOf("P1").get("lastSummary"));
        assertTrue(state.memoryOf("P2").isEmpty());
        assertTrue(state.memoryOf("P3").isEmpty());
    }

    @Test
    void rejectedMissionActionHasNoEventMemoryOrMissionSideEffects() {
        GameRuntimeState state = state();
        state.phase(GamePhase.MISSION_ACTION);

        assertThrows(GameRuleViolationException.class, () -> new GameOrchestrator().applyCollectedActions(
                state,
                Map.of("P1", new PlayerActionResult(
                        null,
                        new MissionAction(MissionChoice.FAIL),
                        null,
                        memoryUpdate("illegal fail", 0L),
                        Map.of()))));

        assertTrue(state.events().isEmpty());
        assertTrue(state.memoryOf("P1").isEmpty());
        assertTrue(state.currentMissionChoices().isEmpty());
    }

    private MemoryUpdate memoryUpdate(String summary, long observedThrough) {
        return new MemoryUpdate(
                Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(),
                Map.of("mode", "EVIDENCE_GATHERING"), Map.of("speechAct", "STATE_OPINION"),
                List.of(), Map.of(), observedThrough, "EVIDENCE_GATHERING", summary);
    }

    private GameRuntimeState state() {
        List<PlayerRegistration> players = List.of(
                new PlayerRegistration("P1", 1, "P1", PlayerControllerType.SCRIPTED),
                new PlayerRegistration("P2", 2, "P2", PlayerControllerType.SCRIPTED),
                new PlayerRegistration("P3", 3, "P3", PlayerControllerType.SCRIPTED));
        GameRuntimeState state = new GameRuntimeState(new GameSetup("g1", "rules", null, "setup",
                new SetupTemplate("setup", 3, true, List.of()), 1L, Map.of(), players));
        state.putRoleAssignment(role("P1", 1, "LOYAL_SERVANT", Camp.GOOD));
        state.putRoleAssignment(role("P2", 2, "ASSASSIN", Camp.EVIL));
        state.putRoleAssignment(role("P3", 3, "LOYAL_SERVANT", Camp.GOOD));
        return state;
    }

    private RoleAssignment role(String playerId, int seatNo, String roleId, Camp camp) {
        return new RoleAssignment("g1", playerId, seatNo, roleId, camp,
                new PlayerPrivateKnowledge(List.of(), List.of()), Instant.now());
    }
}
