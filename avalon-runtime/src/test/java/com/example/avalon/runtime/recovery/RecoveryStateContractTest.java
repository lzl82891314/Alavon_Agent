package com.example.avalon.runtime.recovery;

import com.example.avalon.core.game.enums.DiscussionStage;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.model.PublicSpeechAction;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameRuntimeStateSnapshot;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.model.PlayerRegistration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecoveryStateContractTest {
    @Test
    void snapshotRestorePreservesObservationCursorAndDiscussionProtocolState() {
        GameRuntimeState state = state();
        state.phase(GamePhase.DISCUSSION);
        state.resetDiscussion();
        state.advanceDiscussion(speech("STATE_OPINION", List.of()), 1);
        state.advanceDiscussion(speech("STATE_OPINION", List.of()), 2);
        state.advanceDiscussion(speech("STATE_OPINION", List.of()), 3);
        state.advanceDiscussion(speech("QUESTION", List.of("P3")), 4);
        state.memoryOf("P1").putAll(new LinkedHashMap<>(Map.of(
                "lastObservedSequence", 4L,
                "agentInstanceId", "P1:primary")));

        GameRuntimeState restored = GameRuntimeState.restore(GameRuntimeStateSnapshot.from(state));

        assertEquals(DiscussionStage.CHALLENGE_WINDOW, restored.discussionStage());
        assertEquals(1, restored.discussionSpeakerIndex());
        assertEquals(List.of("P3"), restored.discussionResponseQueue());
        assertEquals(4L, restored.discussionResponseEvents().get("P3"));
        assertEquals(4L, restored.memoryOf("P1").get("lastObservedSequence"));
        assertEquals("P1:primary", restored.memoryOf("P1").get("agentInstanceId"));

        restored.advanceDiscussion(speech("CHALLENGE_CONSISTENCY", List.of()), 5);
        assertEquals(DiscussionStage.TARGETED_RESPONSES, restored.discussionStage());
        assertEquals("P3", restored.currentDiscussionSpeaker().playerId());
    }

    private PublicSpeechAction speech(String speechAct, List<String> mentions) {
        return new PublicSpeechAction("evidence-based statement", speechAct, mentions, List.of());
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
