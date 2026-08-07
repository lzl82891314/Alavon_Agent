package com.example.avalon.core.game.rule;

import com.example.avalon.core.common.exception.GameRuleViolationException;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.game.model.AllowedActionSet;
import com.example.avalon.core.game.model.AssassinationAction;
import com.example.avalon.core.game.model.GamePlayer;
import com.example.avalon.core.game.model.GameRuleContext;
import com.example.avalon.core.game.model.GameSession;
import com.example.avalon.core.game.model.MissionAction;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PublicSpeechAction;
import com.example.avalon.core.game.model.TeamProposalAction;
import com.example.avalon.core.game.model.TeamVoteAction;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.core.role.model.RoleDefinition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ClassicAvalonRuleEngine implements GameRuleEngine {
    @Override
    public AllowedActionSet allowedActions(GameRuleContext context, String playerId) {
        GameSession session = context.session();
        GamePlayer player = context.playerById(playerId);
        RoleDefinition roleDefinition = context.roleDefinitionByPlayerId(playerId);
        Set<PlayerActionType> allowed = EnumSet.noneOf(PlayerActionType.class);

        if (session.status() == GameStatus.ENDED || session.phase() == GamePhase.GAME_END) {
            return new AllowedActionSet(session.gameId(), playerId, player.seatNo(), allowed);
        }

        switch (session.phase()) {
            case DISCUSSION, ROUND_START, ROLE_REVEAL -> allowed.add(PlayerActionType.PUBLIC_SPEECH);
            case TEAM_PROPOSAL -> {
                if (Objects.equals(session.currentLeaderSeat(), player.seatNo()) && roleDefinition.canLead()) {
                    allowed.add(PlayerActionType.TEAM_PROPOSAL);
                }
            }
            case TEAM_VOTE -> {
                if (roleDefinition.canVote()) {
                    allowed.add(PlayerActionType.TEAM_VOTE);
                }
            }
            case MISSION_ACTION -> {
                if (session.currentTeamPlayerIds().contains(player.playerId()) && roleDefinition.canJoinMission()) {
                    allowed.add(PlayerActionType.MISSION_ACTION);
                }
            }
            case ASSASSINATION -> {
                if (roleDefinition.canAssassinate()) {
                    allowed.add(PlayerActionType.ASSASSINATION);
                }
            }
            case MISSION_RESOLUTION, GAME_END, WAITING_FOR_HUMAN_INPUT -> {
            }
        }
        return new AllowedActionSet(session.gameId(), playerId, player.seatNo(), allowed);
    }

    @Override
    public void validateAction(GameRuleContext context, String actorPlayerId, PlayerAction action) {
        GameSession session = context.session();
        GamePlayer actor = context.playerById(actorPlayerId);
        AllowedActionSet allowedActionSet = allowedActions(context, actorPlayerId);
        if (!allowedActionSet.allows(action.actionType())) {
            throw new GameRuleViolationException("Action " + action.actionType() + " is not allowed for player " + actorPlayerId + " in phase " + session.phase());
        }

        switch (action.actionType()) {
            case PUBLIC_SPEECH -> {
                if (!(action instanceof PublicSpeechAction publicSpeechAction) || publicSpeechAction.speechText() == null) {
                    throw new GameRuleViolationException("Speech text must not be null");
                }
            }
            case TEAM_PROPOSAL -> {
                if (!(action instanceof TeamProposalAction proposalAction)) {
                    throw new GameRuleViolationException("Invalid team proposal action");
                }
                int teamSize = context.ruleSet().teamSizeForRound(session.roundNo());
                if (proposalAction.selectedPlayerIds().size() != teamSize) {
                    throw new GameRuleViolationException("Team size mismatch for round " + session.roundNo());
                }
                if (proposalAction.selectedPlayerIds().stream().distinct().count() != proposalAction.selectedPlayerIds().size()) {
                    throw new GameRuleViolationException("Team proposal contains duplicate player ids");
                }
                if (!Objects.equals(actor.seatNo(), session.currentLeaderSeat())) {
                    throw new GameRuleViolationException("Only current leader may propose a team");
                }
                proposalAction.selectedPlayerIds().forEach(playerId -> context.playerById(playerId));
            }
            case TEAM_VOTE -> {
                if (!(action instanceof TeamVoteAction voteAction)) {
                    throw new GameRuleViolationException("Invalid team vote action");
                }
                if (voteAction.vote() == null) {
                    throw new GameRuleViolationException("Vote choice must not be null");
                }
            }
            case MISSION_ACTION -> {
                if (!(action instanceof MissionAction missionAction)) {
                    throw new GameRuleViolationException("Invalid mission action");
                }
                if (missionAction.choice() == null) {
                    throw new GameRuleViolationException("Mission choice must not be null");
                }
                if (!session.currentTeamPlayerIds().contains(actorPlayerId)) {
                    throw new GameRuleViolationException("Only team members may submit mission actions");
                }
                if (missionAction.choice() == MissionChoice.FAIL
                        && context.roleDefinitionByPlayerId(actorPlayerId).camp() != Camp.EVIL) {
                    throw new GameRuleViolationException("Good players may not submit FAIL mission actions");
                }
            }
            case ASSASSINATION -> {
                if (!(action instanceof AssassinationAction assassinationAction)) {
                    throw new GameRuleViolationException("Invalid assassination action");
                }
                if (assassinationAction.targetPlayerId() == null) {
                    throw new GameRuleViolationException("Assassination target must not be null");
                }
            }
        }
    }

    @Override
    public GameSession applyAction(GameRuleContext context, String actorPlayerId, PlayerAction action) {
        validateAction(context, actorPlayerId, action);
        GameSession session = context.session();
        Instant now = Instant.now();

        return switch (action.actionType()) {
            case PUBLIC_SPEECH -> session.withPhase(session.phase(), now);
            case TEAM_PROPOSAL -> applyTeamProposal(context, session, (TeamProposalAction) action, now);
            case TEAM_VOTE -> applyTeamVote(context, session, actorPlayerId, (TeamVoteAction) action, now);
            case MISSION_ACTION -> applyMissionAction(context, session, actorPlayerId, (MissionAction) action, now);
            case ASSASSINATION -> applyAssassination(context, session, actorPlayerId, (AssassinationAction) action, now);
        };
    }

    @Override
    public GamePhase nextPhase(GameRuleContext context) {
        GameSession session = context.session();
        if (session.status() == GameStatus.ENDED) {
            return GamePhase.GAME_END;
        }
        return switch (session.phase()) {
            case ROLE_REVEAL, ROUND_START -> GamePhase.DISCUSSION;
            case DISCUSSION -> GamePhase.TEAM_PROPOSAL;
            case TEAM_PROPOSAL -> GamePhase.TEAM_VOTE;
            case TEAM_VOTE -> session.currentTeamVotes().size() == context.playerCount()
                    ? GamePhase.MISSION_ACTION
                    : GamePhase.TEAM_VOTE;
            case MISSION_ACTION -> GamePhase.MISSION_RESOLUTION;
            case MISSION_RESOLUTION -> session.successfulMissionCount() >= 3 ? GamePhase.ASSASSINATION : GamePhase.DISCUSSION;
            case ASSASSINATION -> GamePhase.GAME_END;
            case GAME_END -> GamePhase.GAME_END;
            case WAITING_FOR_HUMAN_INPUT -> GamePhase.WAITING_FOR_HUMAN_INPUT;
        };
    }

    @Override
    public boolean isGameEnded(GameRuleContext context) {
        return context.session().status() == GameStatus.ENDED || context.session().phase() == GamePhase.GAME_END;
    }

    private GameSession applyTeamProposal(GameRuleContext context, GameSession session, TeamProposalAction action, Instant now) {
        return session.toBuilder()
                .currentTeamPlayerIds(action.selectedPlayerIds())
                .currentTeamVotes(Map.of())
                .currentMissionChoices(Map.of())
                .phase(GamePhase.TEAM_VOTE)
                .updatedAt(now)
                .build();
    }

    private GameSession applyTeamVote(GameRuleContext context, GameSession session, String actorPlayerId, TeamVoteAction action, Instant now) {
        GameSession withVote = session.recordTeamVote(actorPlayerId, action.vote(), now);
        if (withVote.currentTeamVotes().size() < context.playerCount()) {
            return withVote;
        }

        long approves = withVote.currentTeamVotes().values().stream().filter(VoteChoice.APPROVE::equals).count();
        long rejects = withVote.currentTeamVotes().values().stream().filter(VoteChoice.REJECT::equals).count();
        if (approves > rejects) {
            return withVote.toBuilder()
                    .phase(GamePhase.MISSION_ACTION)
                    .currentTeamVotes(Map.of())
                    .updatedAt(now)
                    .build();
        }

        int failedTeamVoteCount = withVote.failedTeamVoteCount() + 1;
        if (failedTeamVoteCount >= 5) {
            return withVote.withWinner(Camp.EVIL, "Five rejected team votes", now);
        }
        return withVote.toBuilder()
                .failedTeamVoteCount(failedTeamVoteCount)
                .phase(GamePhase.DISCUSSION)
                .currentTeamPlayerIds(List.of())
                .currentTeamVotes(Map.of())
                .currentMissionChoices(Map.of())
                .currentLeaderSeat(nextSeat(withVote.currentLeaderSeat(), context.playerCount()))
                .updatedAt(now)
                .build();
    }

    private GameSession applyMissionAction(GameRuleContext context, GameSession session, String actorPlayerId, MissionAction action, Instant now) {
        GameSession withChoice = session.recordMissionChoice(actorPlayerId, action.choice(), now);
        if (withChoice.currentMissionChoices().size() < withChoice.currentTeamPlayerIds().size()) {
            return withChoice;
        }

        long failCount = withChoice.currentMissionChoices().values().stream().filter(MissionChoice.FAIL::equals).count();
        int threshold = context.ruleSet().failThresholdForRound(withChoice.roundNo());
        if (failCount >= threshold) {
            int failedMissionCount = withChoice.failedMissionCount() + 1;
            if (failedMissionCount >= 3) {
                return withChoice.withWinner(Camp.EVIL, "Three failed missions", now);
            }
            return withChoice.toBuilder()
                    .failedMissionCount(failedMissionCount)
                    .roundNo(withChoice.roundNo() + 1)
                    .phase(GamePhase.DISCUSSION)
                    .currentTeamPlayerIds(List.of())
                    .currentTeamVotes(Map.of())
                    .currentMissionChoices(Map.of())
                    .currentLeaderSeat(nextSeat(withChoice.currentLeaderSeat(), context.playerCount()))
                    .updatedAt(now)
                    .build();
        }

        int successfulMissionCount = withChoice.successfulMissionCount() + 1;
        if (successfulMissionCount >= 3) {
            if (context.ruleSet().assassinationRule() != null && context.ruleSet().assassinationRule().enabled()) {
                return withChoice.toBuilder()
                        .successfulMissionCount(successfulMissionCount)
                        .phase(GamePhase.ASSASSINATION)
                        .currentTeamPlayerIds(List.of())
                        .currentTeamVotes(Map.of())
                        .currentMissionChoices(Map.of())
                        .updatedAt(now)
                        .build();
            }
            return withChoice.withWinner(Camp.GOOD, "Three successful missions", now);
        }
        return withChoice.toBuilder()
                .successfulMissionCount(successfulMissionCount)
                .roundNo(withChoice.roundNo() + 1)
                .phase(GamePhase.DISCUSSION)
                .currentTeamPlayerIds(List.of())
                .currentTeamVotes(Map.of())
                .currentMissionChoices(Map.of())
                .currentLeaderSeat(nextSeat(withChoice.currentLeaderSeat(), context.playerCount()))
                .updatedAt(now)
                .build();
    }

    private GameSession applyAssassination(GameRuleContext context, GameSession session, String actorPlayerId, AssassinationAction action, Instant now) {
        if (context.ruleSet().assassinationRule() == null || !context.ruleSet().assassinationRule().enabled()) {
            throw new GameRuleViolationException("Assassination phase is disabled");
        }
        RoleAssignment assassinAssignment = context.roleAssignments().stream()
                .filter(assignment -> assignment.roleId().equals(context.ruleSet().assassinationRule().assassinRoleId()))
                .findFirst()
                .orElseThrow(() -> new GameRuleViolationException("Assassin role assignment not found"));
        RoleAssignment targetAssignment = context.roleAssignments().stream()
                .filter(assignment -> Objects.equals(assignment.playerId(), action.targetPlayerId()))
                .findFirst()
                .orElseThrow(() -> new GameRuleViolationException("Assassination target not found"));

        if (!Objects.equals(actorPlayerId, assassinAssignment.playerId())) {
            throw new GameRuleViolationException("Assassin action is not bound to assassin player");
        }

        if (Objects.equals(targetAssignment.roleId(), context.ruleSet().assassinationRule().merlinRoleId())) {
            return session.withWinner(Camp.EVIL, "Merlin was assassinated", now);
        }
        return session.withWinner(Camp.GOOD, "Assassination missed Merlin", now);
    }

    private int nextSeat(Integer currentSeat, int playerCount) {
        int seat = currentSeat == null ? 1 : currentSeat + 1;
        return seat > playerCount ? 1 : seat;
    }
}
