package com.example.avalon.runtime.service;

import com.example.avalon.core.game.enums.PlayerConnectionState;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.game.model.GamePlayer;
import com.example.avalon.core.game.model.GameRuleContext;
import com.example.avalon.core.game.model.GameSession;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.model.PlayerRegistration;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuntimeCoreContextFactory {
    public GameRuleContext toRuleContext(GameRuntimeState state) {
        return new GameRuleContext(
                toGameSession(state),
                toGamePlayers(state.setup()),
                state.roleAssignments().values().stream().toList(),
                state.runtimeRuleSetDefinition(),
                state.runtimeSetupTemplate(),
                state.setup().roleDefinitions()
        );
    }

    public GameSession toWaitingSession(GameSetup setup, Instant now) {
        return GameSession.createWaiting(
                setup.gameId(),
                setup.ruleSetId(),
                setup.ruleSetDefinition().version(),
                setup.setupTemplateId(),
                setup.seed(),
                now,
                now
        );
    }

    public List<GamePlayer> toGamePlayers(GameSetup setup) {
        return setup.players().stream()
                .map(player -> toGamePlayer(setup.gameId(), player))
                .toList();
    }

    private GameSession toGameSession(GameRuntimeState state) {
        Instant createdAt = state.events().isEmpty() ? state.updatedAt() : state.events().get(0).createdAt();
        return GameSession.builder()
                .gameId(state.generatedGameId())
                .ruleSetId(state.setup().ruleSetId())
                .ruleSetVersion(state.setup().ruleSetDefinition().version())
                .setupTemplateId(state.setup().setupTemplateId())
                .status(state.status())
                .phase(state.phase())
                .roundNo(state.roundNo())
                .failedTeamVoteCount(state.failedTeamVoteCount())
                .randomSeed(state.setup().seed())
                .currentLeaderSeat(state.currentLeaderSeat())
                .successfulMissionCount(state.approvedMissionRounds().size())
                .failedMissionCount(state.failedMissionRounds().size())
                .currentTeamPlayerIds(currentTeamPlayerIds(state))
                .currentTeamVotes(currentTeamVotes(state))
                .currentMissionChoices(currentMissionChoices(state))
                .winnerCamp(state.winnerCamp())
                .winnerReason(null)
                .currentAssassinationTargetPlayerId(null)
                .createdAt(createdAt)
                .updatedAt(state.updatedAt())
                .build();
    }

    private List<String> currentTeamPlayerIds(GameRuntimeState state) {
        return state.currentProposalTeam().stream()
                .map(state::playerBySeat)
                .map(player -> player.playerId())
                .toList();
    }

    private Map<String, VoteChoice> currentTeamVotes(GameRuntimeState state) {
        Map<String, VoteChoice> votes = new LinkedHashMap<>();
        state.currentVotes().forEach((seatNo, vote) -> votes.put(state.playerBySeat(seatNo).playerId(), vote));
        return votes;
    }

    private Map<String, MissionChoice> currentMissionChoices(GameRuntimeState state) {
        Map<String, MissionChoice> missionChoices = new LinkedHashMap<>();
        state.currentMissionChoices().forEach((seatNo, choice) -> missionChoices.put(state.playerBySeat(seatNo).playerId(), choice));
        return missionChoices;
    }

    private GamePlayer toGamePlayer(String gameId, PlayerRegistration player) {
        return new GamePlayer(
                gameId,
                player.playerId(),
                player.seatNo(),
                player.displayName(),
                player.controllerType(),
                null,
                PlayerConnectionState.DISCONNECTED
        );
    }
}
