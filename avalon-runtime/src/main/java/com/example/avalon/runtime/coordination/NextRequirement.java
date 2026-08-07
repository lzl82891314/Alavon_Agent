package com.example.avalon.runtime.coordination;

import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.player.enums.PlayerControllerType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public sealed interface NextRequirement permits
        AutomaticTransitionRequirement,
        SinglePlayerActionRequirement,
        ParallelPlayerActionRequirement,
        ExternalPlayerActionRequirement,
        TerminalRequirement {
    String gameId();
    long sourceGameVersion();
    GamePhase phase();
}

record AutomaticTransitionRequirement(String gameId, long sourceGameVersion, GamePhase phase) implements NextRequirement {}

record SinglePlayerActionRequirement(
        String gameId,
        long sourceGameVersion,
        GamePhase phase,
        String playerId,
        String actionType,
        PlayerControllerType controllerType,
        Instant deadline
) implements NextRequirement {}

record ParallelPlayerActionRequirement(
        String gameId,
        long sourceGameVersion,
        GamePhase phase,
        String actionType,
        Set<String> requiredPlayers,
        Instant deadline
) implements NextRequirement {}

record ExternalPlayerActionRequirement(
        String gameId,
        long sourceGameVersion,
        GamePhase phase,
        String actionType,
        List<String> requiredPlayers,
        Instant deadline
) implements NextRequirement {}

record TerminalRequirement(String gameId, long sourceGameVersion, GamePhase phase) implements NextRequirement {}
