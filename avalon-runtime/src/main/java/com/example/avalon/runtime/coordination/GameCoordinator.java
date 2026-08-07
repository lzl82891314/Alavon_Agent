package com.example.avalon.runtime.coordination;

import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.model.PlayerActionResult;
import com.example.avalon.core.player.controller.PlayerController;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.PlayerRegistration;

import java.util.Optional;

public interface GameCoordinator {
    AdvanceResult advance(String gameId);
    AdvanceResult runUntilBlocked(String gameId, int budget);
    SubmissionResult submit(ActionSubmission submission);
    Optional<ActionBatch> findActiveBatch(String gameId);
}
