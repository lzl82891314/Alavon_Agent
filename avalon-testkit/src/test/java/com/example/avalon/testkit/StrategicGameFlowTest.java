package com.example.avalon.testkit;

import com.example.avalon.core.game.enums.GameStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicGameFlowTest {
    @Test
    void boundedDiscussionProtocolStillCompletesAFullGame() {
        var result = ScriptedAvalonFixture.orchestrator().runToEnd(
                ScriptedAvalonFixture.classicFivePlayerSetup(42L));

        assertEquals(GameStatus.ENDED, result.state().status());
        long publicSpeeches = result.events().stream()
                .filter(event -> "PLAYER_ACTION".equals(event.type()))
                .filter(event -> "PUBLIC_SPEECH".equals(event.payload().get("actionType")))
                .count();
        assertTrue(publicSpeeches >= 8, "discussion should include more than one fixed statement per player");
    }
}
