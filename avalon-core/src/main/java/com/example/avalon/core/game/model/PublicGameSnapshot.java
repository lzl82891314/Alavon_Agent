package com.example.avalon.core.game.model;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;

import java.time.Instant;
import java.util.List;

public record PublicGameSnapshot(
        String gameId,
        GameStatus status,
        GamePhase phase,
        Integer roundNo,
        Integer failedTeamVoteCount,
        Integer successfulMissionCount,
        Integer failedMissionCount,
        Integer currentLeaderSeat,
        List<String> currentTeamPlayerIds,
        Camp winnerCamp,
        String winnerReason,
        List<PublicPlayerSummary> players,
        Instant updatedAt
) {
    public PublicGameSnapshot {
        currentTeamPlayerIds = currentTeamPlayerIds == null ? List.of() : List.copyOf(currentTeamPlayerIds);
        players = players == null ? List.of() : List.copyOf(players);
    }
}

