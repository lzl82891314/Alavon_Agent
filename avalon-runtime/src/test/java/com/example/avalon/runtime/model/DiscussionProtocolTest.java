package com.example.avalon.runtime.model;

import com.example.avalon.core.game.enums.DiscussionStage;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.model.PublicSpeechAction;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.setup.model.SetupTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscussionProtocolTest {
    @Test
    void runsOpeningChallengesResponsesAndLeaderSynthesis() {
        GameRuntimeState state = state();
        state.phase(GamePhase.DISCUSSION);
        state.resetDiscussion();

        state.advanceDiscussion(speech("STATE_OPINION", List.of()), 1);
        state.advanceDiscussion(speech("STATE_OPINION", List.of()), 2);
        state.advanceDiscussion(speech("STATE_OPINION", List.of()), 3);
        assertEquals(DiscussionStage.CHALLENGE_WINDOW, state.discussionStage());

        state.advanceDiscussion(speech("QUESTION", List.of("P3")), 4);
        state.advanceDiscussion(speech("CHALLENGE_CONSISTENCY", List.of("P1")), 5);
        assertEquals(DiscussionStage.TARGETED_RESPONSES, state.discussionStage());
        assertEquals("P3", state.currentDiscussionSpeaker().playerId());
        assertEquals(4L, state.discussionDirectiveFor("P3").replyToEventSequence());

        state.advanceDiscussion(new PublicSpeechAction("回应", "ANSWER", List.of(), List.of(4L)), 6);
        state.advanceDiscussion(new PublicSpeechAction("回应", "ANSWER", List.of(), List.of(5L)), 7);
        assertEquals(DiscussionStage.LEADER_SYNTHESIS, state.discussionStage());
        assertEquals("P1", state.currentDiscussionSpeaker().playerId());

        state.advanceDiscussion(speech("LEADER_SUMMARY", List.of()), 8);
        assertEquals(GamePhase.TEAM_PROPOSAL, state.phase());
    }

    private PublicSpeechAction speech(String act, List<String> mentions) {
        return new PublicSpeechAction("有证据的发言", act, mentions, List.of());
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
