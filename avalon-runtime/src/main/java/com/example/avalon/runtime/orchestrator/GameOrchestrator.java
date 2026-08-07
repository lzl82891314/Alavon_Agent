package com.example.avalon.runtime.orchestrator;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.common.exception.GameRuleViolationException;
import com.example.avalon.core.game.model.AssassinationAction;
import com.example.avalon.core.game.model.MissionAction;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PlayerActionResult;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.TeamProposalAction;
import com.example.avalon.core.game.model.TeamVoteAction;
import com.example.avalon.core.player.controller.PlayerController;
import com.example.avalon.core.player.controller.PlayerActionGenerationException;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.runtime.controller.PlayerControllerResolver;
import com.example.avalon.runtime.engine.ConfigDrivenGameRuleEngine;
import com.example.avalon.runtime.engine.GameRuleEngine;
import com.example.avalon.runtime.engine.RoleAssignmentService;
import com.example.avalon.runtime.engine.VisibilityService;
import com.example.avalon.runtime.model.GameEvent;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.model.PlayerRegistration;
import com.example.avalon.runtime.model.RuntimeAuditEntry;
import com.example.avalon.runtime.service.GameSessionService;
import com.example.avalon.runtime.service.ResolvedLlmConfigInitializer;
import com.example.avalon.runtime.service.SeededLeaderSelector;
import com.example.avalon.runtime.service.TurnContextBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import java.util.Comparator;

public class GameOrchestrator {
    private final GameSessionService sessionService;
    private final GameRuleEngine ruleEngine;
    private final RoleAssignmentService roleAssignmentService;
    private final VisibilityService visibilityService;
    private final TurnContextBuilder turnContextBuilder;
    private final PlayerControllerResolver controllerResolver;
    private final ResolvedLlmConfigInitializer resolvedLlmConfigInitializer;

    public GameOrchestrator() {
        this(new GameSessionService(),
                new ConfigDrivenGameRuleEngine(),
                new RoleAssignmentService(),
                new VisibilityService(),
                new PlayerControllerResolver(),
                ResolvedLlmConfigInitializer.NOOP);
    }

    public GameOrchestrator(GameSessionService sessionService,
                            GameRuleEngine ruleEngine,
                            RoleAssignmentService roleAssignmentService,
                            VisibilityService visibilityService,
                            PlayerControllerResolver controllerResolver) {
        this(sessionService, ruleEngine, roleAssignmentService, visibilityService, controllerResolver, ResolvedLlmConfigInitializer.NOOP);
    }

    public GameOrchestrator(GameSessionService sessionService,
                            GameRuleEngine ruleEngine,
                            RoleAssignmentService roleAssignmentService,
                            VisibilityService visibilityService,
                            PlayerControllerResolver controllerResolver,
                            ResolvedLlmConfigInitializer resolvedLlmConfigInitializer) {
        this.sessionService = sessionService;
        this.ruleEngine = ruleEngine;
        this.roleAssignmentService = roleAssignmentService;
        this.visibilityService = visibilityService;
        this.turnContextBuilder = new TurnContextBuilder(visibilityService);
        this.controllerResolver = controllerResolver;
        this.resolvedLlmConfigInitializer = resolvedLlmConfigInitializer;
    }

    public GameRuntimeState createGame(GameSetup setup) {
        GameRuntimeState state = sessionService.create(setup);
        state.appendEvent("GAME_CREATED", GamePhase.ROLE_REVEAL, "SYSTEM", Map.of("gameId", state.generatedGameId()));
        sessionService.save(state);
        return state;
    }

    public GameRuntimeState start(String gameId) {
        GameRuntimeState state = sessionService.require(gameId);
        if (state.status() != GameStatus.WAITING) {
            return state;
        }
        List<RoleAssignment> assignments = roleAssignmentService.assignRoles(state.setup());
        assignments.forEach(state::putRoleAssignment);
        state.replaceResolvedLlmControllerConfigs(resolvedLlmConfigInitializer.resolve(state));
        state.status(GameStatus.RUNNING);
        state.phase(GamePhase.DISCUSSION);
        state.roundNo(1);
        state.currentLeaderSeat(SeededLeaderSelector.initialLeaderSeat(state.players(), state.setup().seed()));
        state.resetRoundTurnState();
        state.appendEvent("GAME_STARTED", GamePhase.DISCUSSION, "SYSTEM", Map.of(
                "leaderSeat", state.currentLeaderSeat(),
                "playerCount", state.playerCount()));
        assignments.forEach(assignment -> state.appendEvent("ROLE_ASSIGNED", GamePhase.ROLE_REVEAL, assignment.playerId(), Map.of(
                "seatNo", assignment.seatNo(),
                "roleId", assignment.roleId(),
                "camp", assignment.camp().name(),
                "privateKnowledge", assignment.privateKnowledge().notes())));
        sessionService.save(state);
        return state;
    }

    public GameRuntimeState step(String gameId) {
        GameRuntimeState state = sessionService.require(gameId);
        if (state.status() != GameStatus.RUNNING) {
            return state;
        }
        switch (state.phase()) {
            case DISCUSSION -> processDiscussionStep(state);
            case TEAM_PROPOSAL -> processProposalStep(state);
            case TEAM_VOTE -> processVoteStep(state);
            case MISSION_ACTION -> processMissionStep(state);
            case MISSION_RESOLUTION -> resolveMission(state);
            case ASSASSINATION -> processAssassination(state);
            case ROLE_REVEAL, GAME_END, ROUND_START, WAITING_FOR_HUMAN_INPUT -> {
            }
        }
        sessionService.save(state);
        return state;
    }

    public GameRunResult runToEnd(String gameId) {
        GameRuntimeState state = start(gameId);
        List<String> transcript = new ArrayList<>();
        int safety = 500;
        while (state.status() == GameStatus.RUNNING && safety-- > 0) {
            step(gameId);
            GameEvent lastEvent = state.events().isEmpty() ? null : state.events().get(state.events().size() - 1);
            if (lastEvent != null) {
                transcript.add(lastEvent.type() + ":" + lastEvent.payload());
            }
        }
        if (safety <= 0) {
            throw new IllegalStateException("Run-to-end exceeded safety limit");
        }
        return new GameRunResult(state, state.events(), transcript);
    }

    public GameRunResult runToEnd(GameSetup setup) {
        GameRuntimeState state = createGame(setup);
        return runToEnd(state.generatedGameId());
    }

    /** Applies already collected actions without invoking any controller. */
    public void applyCollectedActions(GameRuntimeState state, Map<String, PlayerActionResult> results) {
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("Collected actions must not be empty");
        }
        List<PlayerRegistration> players = results.keySet().stream()
                .map(state::playerById)
                .sorted(Comparator.comparingInt(PlayerRegistration::seatNo))
                .toList();
        switch (state.phase()) {
            case DISCUSSION -> {
                PlayerRegistration player = players.get(0);
                recordAction(state, player, results.get(player.playerId()));
                state.discussionSpeakerIndex(state.discussionSpeakerIndex() + 1);
                if (state.discussionSpeakerIndex() >= state.playerCount()) {
                    state.discussionSpeakerIndex(0);
                    state.phase(GamePhase.TEAM_PROPOSAL);
                }
            }
            case TEAM_PROPOSAL -> {
                PlayerRegistration leader = players.get(0);
                PlayerActionResult result = results.get(leader.playerId());
                recordAction(state, leader, result);
                TeamProposalAction proposal = (TeamProposalAction) result.action();
                state.clearProposalState();
                proposal.selectedPlayerIds().stream().map(state::playerById).map(PlayerRegistration::seatNo)
                        .forEach(state::addCurrentProposalSeat);
                state.appendEvent("TEAM_PROPOSED", GamePhase.TEAM_PROPOSAL, leader.playerId(), Map.of("playerIds", proposal.selectedPlayerIds()));
                state.phase(GamePhase.TEAM_VOTE);
                state.voteIndex(0);
            }
            case TEAM_VOTE -> {
                for (PlayerRegistration voter : players) {
                    PlayerActionResult result = results.get(voter.playerId());
                    recordAction(state, voter, result);
                    TeamVoteAction vote = (TeamVoteAction) result.action();
                    state.putVote(voter.seatNo(), vote.vote());
                }
                state.appendEvent("TEAM_VOTES_REVEALED", GamePhase.TEAM_VOTE, "SYSTEM", Map.of("votes", state.currentVotes()));
                long approves = state.currentVotes().values().stream().filter(v -> v == VoteChoice.APPROVE).count();
                if (approves > state.currentVotes().size() - approves) {
                    state.phase(GamePhase.MISSION_ACTION);
                    state.clearMissionState();
                    state.currentProposalTeam().forEach(state::addCurrentMissionSeat);
                } else {
                    state.failedTeamVoteCount(state.failedTeamVoteCount() + 1);
                    state.appendEvent("TEAM_VOTE_REJECTED", GamePhase.TEAM_VOTE, "SYSTEM", Map.of("failedTeamVoteCount", state.failedTeamVoteCount()));
                    if (state.failedTeamVoteCount() >= 5) {
                        endGame(state, Camp.EVIL, GamePhase.GAME_END);
                    } else {
                        state.clearProposalState();
                        state.currentLeaderSeat(state.nextSeatAfter(state.currentLeaderSeat()));
                        state.phase(GamePhase.DISCUSSION);
                        state.discussionSpeakerIndex(0);
                    }
                }
                state.voteIndex(0);
            }
            case MISSION_ACTION -> {
                for (PlayerRegistration player : players) {
                    PlayerActionResult result = results.get(player.playerId());
                    recordAction(state, player, result);
                    MissionAction action = (MissionAction) result.action();
                    RoleAssignment assignment = state.roleAssignments().get(player.playerId());
                    if (assignment != null && assignment.camp() == Camp.GOOD && action.choice() == MissionChoice.FAIL) {
                        throw new GameRuleViolationException("Good players may not submit FAIL mission actions");
                    }
                    state.putMissionChoice(player.seatNo(), action.choice());
                }
                long failCount = state.currentMissionChoices().values().stream()
                        .filter(choice -> choice == MissionChoice.FAIL)
                        .count();
                state.appendEvent("MISSION_RESULT_REVEALED", GamePhase.MISSION_ACTION, "SYSTEM", Map.of(
                        "roundNo", state.roundNo(),
                        "result", failCount > 0 ? "FAILED" : "SUCCESS",
                        "failCount", failCount));
                state.phase(GamePhase.MISSION_RESOLUTION);
            }
            case ASSASSINATION -> {
                PlayerRegistration assassin = players.get(0);
                PlayerActionResult result = results.get(assassin.playerId());
                recordAction(state, assassin, result);
                AssassinationAction action = (AssassinationAction) result.action();
                RoleAssignment target = state.roleAssignments().values().stream().filter(a -> a.playerId().equals(action.targetPlayerId())).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Missing assassination target"));
                state.appendEvent("ASSASSINATION_SUBMITTED", GamePhase.ASSASSINATION, assassin.playerId(), Map.of("targetPlayerId", action.targetPlayerId(), "targetRole", target.roleId()));
                endGame(state, Camp.valueOf(state.setup().ruleSetDefinition().assassinationRule().merlinRoleId().equals(target.roleId()) ? "EVIL" : "GOOD"), GamePhase.GAME_END);
            }
            default -> throw new IllegalStateException("Phase does not accept collected actions: " + state.phase());
        }
        sessionService.save(state);
    }

    private void processDiscussionStep(GameRuntimeState state) {
        PlayerRegistration player = state.playerByIndex(state.discussionSpeakerIndex());
        PlayerTurnContext context = turnContextBuilder.build(state, player);
        PlayerController controller = controllerResolver.resolve(state, player);
        PlayerActionResult result = actForPlayer(state, player, controller, context);
        if (result == null) {
            return;
        }
        recordAction(state, player, result);
        state.discussionSpeakerIndex(state.discussionSpeakerIndex() + 1);
        if (state.discussionSpeakerIndex() >= state.playerCount()) {
            state.discussionSpeakerIndex(0);
            state.phase(GamePhase.TEAM_PROPOSAL);
        }
    }

    private void processProposalStep(GameRuntimeState state) {
        PlayerRegistration leader = state.playerBySeat(state.currentLeaderSeat());
        PlayerTurnContext context = turnContextBuilder.build(state, leader);
        PlayerController controller = controllerResolver.resolve(state, leader);
        PlayerActionResult result = actForPlayer(state, leader, controller, context);
        if (result == null) {
            return;
        }
        recordAction(state, leader, result);
        TeamProposalAction proposal = (TeamProposalAction) result.action();
        state.clearProposalState();
        proposal.selectedPlayerIds().stream()
                .map(state::playerById)
                .map(PlayerRegistration::seatNo)
                .forEach(state::addCurrentProposalSeat);
        state.appendEvent("TEAM_PROPOSED", GamePhase.TEAM_PROPOSAL, leader.playerId(), Map.of("playerIds", proposal.selectedPlayerIds()));
        state.phase(GamePhase.TEAM_VOTE);
        state.voteIndex(0);
    }

    private void processVoteStep(GameRuntimeState state) {
        PlayerRegistration voter = state.playerByIndex(state.voteIndex());
        PlayerTurnContext context = turnContextBuilder.build(state, voter);
        PlayerController controller = controllerResolver.resolve(state, voter);
        PlayerActionResult result = actForPlayer(state, voter, controller, context);
        if (result == null) {
            return;
        }
        recordAction(state, voter, result);
        TeamVoteAction voteAction = (TeamVoteAction) result.action();
        state.putVote(voter.seatNo(), voteAction.vote());
        state.appendEvent("TEAM_VOTE_CAST", GamePhase.TEAM_VOTE, voter.playerId(), Map.of("vote", voteAction.vote().name()));
        state.voteIndex(state.voteIndex() + 1);
        if (state.voteIndex() >= state.playerCount()) {
            long approves = state.currentVotes().values().stream().filter(vote -> vote == VoteChoice.APPROVE).count();
            long rejects = state.currentVotes().size() - approves;
            if (approves > rejects) {
                state.phase(GamePhase.MISSION_ACTION);
                state.clearMissionState();
                state.currentProposalTeam().forEach(state::addCurrentMissionSeat);
            } else {
                state.failedTeamVoteCount(state.failedTeamVoteCount() + 1);
                state.appendEvent("TEAM_VOTE_REJECTED", GamePhase.TEAM_VOTE, "SYSTEM", Map.of(
                        "failedTeamVoteCount", state.failedTeamVoteCount()));
                if (state.failedTeamVoteCount() >= 5) {
                    endGame(state, Camp.EVIL, GamePhase.GAME_END);
                    return;
                }
                state.clearProposalState();
                state.currentLeaderSeat(state.nextSeatAfter(state.currentLeaderSeat()));
                state.phase(GamePhase.DISCUSSION);
                state.discussionSpeakerIndex(0);
            }
            state.voteIndex(0);
        }
    }

    private void processMissionStep(GameRuntimeState state) {
        PlayerRegistration player = state.playerBySeat(state.currentProposalTeam().get(state.missionIndex()));
        PlayerTurnContext context = turnContextBuilder.build(state, player);
        PlayerController controller = controllerResolver.resolve(state, player);
        PlayerActionResult result = actForPlayer(state, player, controller, context);
        if (result == null) {
            return;
        }
        recordAction(state, player, result);
        MissionAction missionAction = (MissionAction) result.action();
        if (context.privateView().camp() == Camp.GOOD && missionAction.choice() == MissionChoice.FAIL) {
            throw new GameRuleViolationException("Good players may not submit FAIL mission actions");
        }
        state.putMissionChoice(player.seatNo(), missionAction.choice());
        state.appendEvent("MISSION_ACTION_CAST", GamePhase.MISSION_ACTION, player.playerId(), Map.of("choice", missionAction.choice().name()));
        state.missionIndex(state.missionIndex() + 1);
        if (state.missionIndex() >= state.currentProposalTeam().size()) {
            state.phase(GamePhase.MISSION_RESOLUTION);
        }
    }

    private void resolveMission(GameRuntimeState state) {
        long fails = state.currentMissionChoices().values().stream().filter(choice -> choice == MissionChoice.FAIL).count();
        if (fails >= ruleEngine.missionFailThresholdForRound(state)) {
            state.addFailedMissionRound(state.roundNo());
            state.appendEvent("MISSION_FAILED", GamePhase.MISSION_RESOLUTION, "SYSTEM", Map.of("roundNo", state.roundNo(), "fails", fails));
        } else {
            state.addApprovedMissionRound(state.roundNo());
            state.appendEvent("MISSION_SUCCESS", GamePhase.MISSION_RESOLUTION, "SYSTEM", Map.of("roundNo", state.roundNo()));
        }
        state.clearProposalState();
        state.clearMissionState();

        if (ruleEngine.shouldEnterAssassination(state)) {
            state.phase(GamePhase.ASSASSINATION);
            return;
        }

        String winner = ruleEngine.resolveWinner(state);
        if (winner != null) {
            endGame(state, Camp.valueOf(winner), GamePhase.GAME_END);
            return;
        }

        state.roundNo(state.roundNo() + 1);
        state.currentLeaderSeat(state.nextSeatAfter(state.currentLeaderSeat()));
        state.phase(GamePhase.DISCUSSION);
        state.discussionSpeakerIndex(0);
    }

    private void processAssassination(GameRuntimeState state) {
        String assassinRoleId = state.setup().ruleSetDefinition().assassinationRule().assassinRoleId();
        String merlinRoleId = state.setup().ruleSetDefinition().assassinationRule().merlinRoleId();
        PlayerRegistration assassin = state.roleAssignments().values().stream()
                .filter(assignment -> assassinRoleId.equals(assignment.roleId()))
                .map(assignment -> state.playerBySeat(assignment.seatNo()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing assassin"));
        PlayerTurnContext context = turnContextBuilder.build(state, assassin);
        PlayerController controller = controllerResolver.resolve(state, assassin);
        PlayerActionResult result = actForPlayer(state, assassin, controller, context);
        if (result == null) {
            return;
        }
        recordAction(state, assassin, result);
        AssassinationAction assassinationAction = (AssassinationAction) result.action();
        RoleAssignment target = state.roleAssignments().values().stream()
                .filter(assignment -> assignment.playerId().equals(assassinationAction.targetPlayerId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing assassination target"));
        state.appendEvent("ASSASSINATION_SUBMITTED", GamePhase.ASSASSINATION, assassin.playerId(), Map.of(
                "targetPlayerId", assassinationAction.targetPlayerId(),
                "targetRole", target.roleId()));
        if (merlinRoleId.equals(target.roleId())) {
            endGame(state, Camp.EVIL, GamePhase.GAME_END);
        } else {
            endGame(state, Camp.GOOD, GamePhase.GAME_END);
        }
    }

    private void endGame(GameRuntimeState state, Camp winner, GamePhase terminalPhase) {
        state.winner(winner);
        state.phase(terminalPhase);
        state.status(GameStatus.ENDED);
        state.appendEvent("GAME_ENDED", terminalPhase, "SYSTEM", Map.of("winner", winner.name()));
    }

    private void recordAction(GameRuntimeState state, PlayerRegistration player, PlayerActionResult result) {
        PlayerAction action = result.action();
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("seatNo", player.seatNo());
        payload.put("actionType", action.actionType().name());
        payload.put("speech", result.publicSpeech() == null ? "" : result.publicSpeech());
        if (result.auditReason() != null) {
            payload.put("auditReason", result.auditReason());
        }
        state.appendEvent("PLAYER_ACTION", state.phase(), player.playerId(), payload);
        RuntimeAuditEntry auditEntry = toAuditEntry(state.events().get(state.events().size() - 1), player, result);
        if (auditEntry != null) {
            state.appendAudit(auditEntry);
        }
    }

    private PlayerActionResult actForPlayer(GameRuntimeState state,
                                            PlayerRegistration player,
                                            PlayerController controller,
                                            PlayerTurnContext context) {
        try {
            return controller.act(context);
        } catch (PlayerActionGenerationException exception) {
            if (player.controllerType() != PlayerControllerType.LLM) {
                throw exception;
            }
            pauseForLlmFailure(state, player, exception);
            return null;
        }
    }

    private void pauseForLlmFailure(GameRuntimeState state,
                                    PlayerRegistration player,
                                    PlayerActionGenerationException exception) {
        state.status(GameStatus.PAUSED);
        state.appendEvent("GAME_PAUSED", state.phase(), player.playerId(), Map.of(
                "reason", "LLM_ACTION_FAILURE",
                "controllerType", player.controllerType().name(),
                "playerId", player.playerId()
        ));
        GameEvent pauseEvent = state.events().get(state.events().size() - 1);
        Map<String, Object> rawMetadata = exception.rawMetadata();
        state.appendAudit(new RuntimeAuditEntry(
                UUID.randomUUID().toString(),
                pauseEvent.seqNo(),
                player.playerId(),
                auditVisibility(rawMetadata),
                mapValue(rawMetadata.get("inputContext")),
                mapValue(rawMetadata.get("rawModelResponse")),
                mapValue(rawMetadata.get("parsedAction")),
                mapValue(rawMetadata.get("auditReason")),
                failedValidation(rawMetadata, exception),
                exception.getMessage(),
                pauseEvent.createdAt()
        ));
    }

    private RuntimeAuditEntry toAuditEntry(GameEvent event, PlayerRegistration player, PlayerActionResult result) {
        Map<String, Object> rawMetadata = result.rawMetadata();
        if (!(rawMetadata.get("inputContext") instanceof Map<?, ?> inputContext)
                && !(rawMetadata.get("rawModelResponse") instanceof Map<?, ?>)
                && result.auditReason() == null) {
            return null;
        }
        return new RuntimeAuditEntry(
                UUID.randomUUID().toString(),
                event.seqNo(),
                player.playerId(),
                auditVisibility(rawMetadata),
                mapValue(rawMetadata.get("inputContext")),
                mapValue(rawMetadata.get("rawModelResponse")),
                parsedAction(result.action()),
                auditReason(result),
                mapValue(rawMetadata.get("validation")),
                stringValue(rawMetadata.get("errorMessage")),
                event.createdAt()
        );
    }

    private String auditVisibility(Map<String, Object> rawMetadata) {
        Object visibility = rawMetadata.get("auditVisibility");
        if (visibility == null) {
            return "ADMIN_ONLY";
        }
        String value = String.valueOf(visibility);
        return value.isBlank() ? "ADMIN_ONLY" : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> copied.put(String.valueOf(key), nestedValue));
            return copied;
        }
        return Map.of();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String string = String.valueOf(value);
        return string.isBlank() ? null : string;
    }

    private Map<String, Object> parsedAction(PlayerAction action) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actionType", action.actionType().name());
        switch (action) {
            case com.example.avalon.core.game.model.PublicSpeechAction speechAction -> payload.put("speechText", speechAction.speechText());
            case TeamProposalAction proposalAction -> payload.put("selectedPlayerIds", proposalAction.selectedPlayerIds());
            case TeamVoteAction voteAction -> payload.put("vote", voteAction.vote().name());
            case MissionAction missionAction -> payload.put("choice", missionAction.choice().name());
            case AssassinationAction assassinationAction -> payload.put("targetPlayerId", assassinationAction.targetPlayerId());
            default -> {
            }
        }
        return payload;
    }

    private Map<String, Object> auditReason(PlayerActionResult result) {
        if (result.auditReason() == null) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("goal", result.auditReason().goal());
        payload.put("reasonSummary", result.auditReason().reasonSummary());
        payload.put("confidence", result.auditReason().confidence());
        payload.put("beliefs", result.auditReason().beliefs());
        return payload;
    }

    private Map<String, Object> failedValidation(Map<String, Object> rawMetadata, PlayerActionGenerationException exception) {
        Map<String, Object> payload = new LinkedHashMap<>(mapValue(rawMetadata.get("validation")));
        payload.put("valid", false);
        putIfAbsent(payload, "errorMessage", exception.getMessage());
        return payload;
    }

    private void putIfAbsent(Map<String, Object> payload, String key, Object value) {
        if (!payload.containsKey(key) && value != null) {
            payload.put(key, value);
        }
    }
}
