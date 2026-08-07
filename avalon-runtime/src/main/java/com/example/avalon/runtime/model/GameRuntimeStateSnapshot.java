package com.example.avalon.runtime.model;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.role.model.RoleAssignment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GameRuntimeStateSnapshot(
        GameSetup setup,
        List<RoleAssignment> roleAssignments,
        Map<String, Map<String, Object>> memoryByPlayerId,
        Map<String, Map<String, Object>> resolvedLlmControllerConfigsByPlayerId,
        List<GameEvent> events,
        List<Integer> approvedMissionRounds,
        List<Integer> failedMissionRounds,
        List<Integer> currentProposalTeam,
        List<Integer> currentMissionTeam,
        Map<Integer, VoteChoice> currentVotes,
        Map<Integer, MissionChoice> currentMissionChoices,
        GameStatus status,
        GamePhase phase,
        int roundNo,
        int currentLeaderSeat,
        int failedTeamVoteCount,
        int discussionSpeakerIndex,
        int voteIndex,
        int missionIndex,
        Camp winnerCamp,
        int sequence,
        Instant updatedAt
) {
    public static GameRuntimeStateSnapshot from(GameRuntimeState state) {
        return new GameRuntimeStateSnapshot(
                state.setup(),
                state.roleAssignments().values().stream().toList(),
                state.memoryByPlayerId(),
                state.resolvedLlmControllerConfigs(),
                state.events(),
                state.approvedMissionRounds(),
                state.failedMissionRounds(),
                state.currentProposalTeam(),
                state.currentMissionTeam(),
                state.currentVotes(),
                state.currentMissionChoices(),
                state.status(),
                state.phase(),
                state.roundNo(),
                state.currentLeaderSeat(),
                state.failedTeamVoteCount(),
                state.discussionSpeakerIndex(),
                state.voteIndex(),
                state.missionIndex(),
                state.winnerCamp(),
                state.events().size(),
                state.updatedAt()
        );
    }
}
