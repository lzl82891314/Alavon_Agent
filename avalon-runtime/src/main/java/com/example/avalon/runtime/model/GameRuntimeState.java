package com.example.avalon.runtime.model;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.PlayerConnectionState;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.game.model.PublicPlayerSummary;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GameRuntimeState {
    private final String gameId;
    private final GameSetup setup;
    private final List<PlayerRegistration> players;
    private final Map<Integer, RoleAssignment> roleAssignmentsBySeat = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> memoryByPlayerId = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> resolvedLlmControllerConfigsByPlayerId = new LinkedHashMap<>();
    private final List<GameEvent> events = new ArrayList<>();
    private final List<RuntimeAuditEntry> auditEntries = new ArrayList<>();
    private final List<Integer> approvedMissionRounds = new ArrayList<>();
    private final List<Integer> failedMissionRounds = new ArrayList<>();
    private final List<Integer> currentProposalTeam = new ArrayList<>();
    private final List<Integer> currentMissionTeam = new ArrayList<>();
    private final Map<Integer, VoteChoice> currentVotes = new LinkedHashMap<>();
    private final Map<Integer, MissionChoice> currentMissionChoices = new LinkedHashMap<>();
    private GameStatus status = GameStatus.WAITING;
    private GamePhase phase = GamePhase.ROLE_REVEAL;
    private int roundNo = 1;
    private int currentLeaderSeat;
    private int failedTeamVoteCount;
    private int discussionSpeakerIndex;
    private int voteIndex;
    private int missionIndex;
    private Camp winnerCamp;
    private int sequence;
    private Instant updatedAt;

    public GameRuntimeState(GameSetup setup) {
        this.setup = Objects.requireNonNull(setup, "setup");
        this.gameId = setup.gameId() == null || setup.gameId().isBlank() ? java.util.UUID.randomUUID().toString() : setup.gameId();
        this.players = new ArrayList<>(setup.players());
        this.players.sort((left, right) -> Integer.compare(left.seatNo(), right.seatNo()));
        this.currentLeaderSeat = this.players.isEmpty() ? 0 : this.players.get(0).seatNo();
        this.updatedAt = Instant.now();
        for (PlayerRegistration player : players) {
            memoryByPlayerId.put(player.playerId(), new LinkedHashMap<>());
        }
    }

    public GameSetup setup() {
        return setup;
    }

    public String gameId() {
        return gameId;
    }

    public String generatedGameId() {
        return gameId;
    }

    public RuleSetDefinition runtimeRuleSetDefinition() {
        return setup.ruleSetDefinition();
    }

    public SetupTemplate runtimeSetupTemplate() {
        return setup.setupTemplate();
    }

    public List<PlayerRegistration> players() {
        return Collections.unmodifiableList(players);
    }

    public List<PublicPlayerSummary> publicPlayers() {
        return players.stream()
                .map(player -> new PublicPlayerSummary(
                        gameId,
                        player.playerId(),
                        player.seatNo(),
                        player.displayName(),
                        player.controllerType(),
                        PlayerConnectionState.DISCONNECTED))
                .toList();
    }

    public PlayerRegistration playerBySeat(int seatNo) {
        return players.stream()
                .filter(player -> player.seatNo() == seatNo)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown seat: " + seatNo));
    }

    public PlayerRegistration playerById(String playerId) {
        return players.stream()
                .filter(player -> player.playerId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown player: " + playerId));
    }

    public PlayerRegistration playerByIndex(int index) {
        if (players.isEmpty()) {
            throw new IllegalStateException("No players registered");
        }
        return players.get(Math.floorMod(index, players.size()));
    }

    public Optional<RoleAssignment> roleAssignmentBySeat(int seatNo) {
        return Optional.ofNullable(roleAssignmentsBySeat.get(seatNo));
    }

    public RoleAssignment requireRoleAssignmentBySeat(int seatNo) {
        return roleAssignmentBySeat(seatNo).orElseThrow(() -> new IllegalStateException("Missing role for seat " + seatNo));
    }

    public void putRoleAssignment(RoleAssignment roleAssignment) {
        roleAssignmentsBySeat.put(roleAssignment.seatNo(), roleAssignment);
    }

    public Map<Integer, RoleAssignment> roleAssignments() {
        return Collections.unmodifiableMap(roleAssignmentsBySeat);
    }

    public Map<String, Map<String, Object>> memoryByPlayerId() {
        return memoryByPlayerId;
    }

    public Map<String, Object> memoryOf(String playerId) {
        return memoryByPlayerId.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>());
    }

    public Map<String, Map<String, Object>> resolvedLlmControllerConfigs() {
        return Collections.unmodifiableMap(resolvedLlmControllerConfigsByPlayerId);
    }

    public Map<String, Object> resolvedLlmControllerConfigOf(String playerId) {
        return resolvedLlmControllerConfigsByPlayerId.get(playerId);
    }

    public void replaceResolvedLlmControllerConfigs(Map<String, Map<String, Object>> configsByPlayerId) {
        resolvedLlmControllerConfigsByPlayerId.clear();
        if (configsByPlayerId == null) {
            return;
        }
        configsByPlayerId.forEach((playerId, config) -> resolvedLlmControllerConfigsByPlayerId.put(
                playerId,
                config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config)));
        updatedAt = Instant.now();
    }

    public List<GameEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public List<RuntimeAuditEntry> auditEntries() {
        return Collections.unmodifiableList(auditEntries);
    }

    public void appendEvent(String type, GamePhase phase, String actorId, Map<String, Object> payload) {
        events.add(new GameEvent(++sequence, type, phase, actorId, new LinkedHashMap<>(payload), Instant.now()));
        updatedAt = Instant.now();
    }

    public void appendAudit(RuntimeAuditEntry auditEntry) {
        auditEntries.add(Objects.requireNonNull(auditEntry, "auditEntry"));
        updatedAt = Instant.now();
    }

    public void clearPendingAudits() {
        auditEntries.clear();
    }

    public void appendRecoveredEvent(GameEvent event) {
        events.add(event);
        sequence = (int) event.seqNo();
        updatedAt = event.createdAt();
    }

    public List<Integer> approvedMissionRounds() {
        return Collections.unmodifiableList(approvedMissionRounds);
    }

    public List<Integer> failedMissionRounds() {
        return Collections.unmodifiableList(failedMissionRounds);
    }

    public List<Integer> currentProposalTeam() {
        return Collections.unmodifiableList(currentProposalTeam);
    }

    public List<Integer> currentMissionTeam() {
        return Collections.unmodifiableList(currentMissionTeam);
    }

    public Map<Integer, VoteChoice> currentVotes() {
        return Collections.unmodifiableMap(currentVotes);
    }

    public Map<Integer, MissionChoice> currentMissionChoices() {
        return Collections.unmodifiableMap(currentMissionChoices);
    }

    public void clearProposalState() {
        currentProposalTeam.clear();
        currentVotes.clear();
        voteIndex = 0;
    }

    public void clearMissionState() {
        currentMissionTeam.clear();
        currentMissionChoices.clear();
        missionIndex = 0;
    }

    public void resetRoundTurnState() {
        discussionSpeakerIndex = 0;
        voteIndex = 0;
        missionIndex = 0;
        clearProposalState();
        clearMissionState();
    }

    public GameStatus status() {
        return status;
    }

    public void status(GameStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public GamePhase phase() {
        return phase;
    }

    public void phase(GamePhase phase) {
        this.phase = phase;
        this.updatedAt = Instant.now();
    }

    public int roundNo() {
        return roundNo;
    }

    public void roundNo(int roundNo) {
        this.roundNo = roundNo;
        this.updatedAt = Instant.now();
    }

    public int currentLeaderSeat() {
        return currentLeaderSeat;
    }

    public void currentLeaderSeat(int currentLeaderSeat) {
        this.currentLeaderSeat = currentLeaderSeat;
        this.updatedAt = Instant.now();
    }

    public int failedTeamVoteCount() {
        return failedTeamVoteCount;
    }

    public void failedTeamVoteCount(int failedTeamVoteCount) {
        this.failedTeamVoteCount = failedTeamVoteCount;
        this.updatedAt = Instant.now();
    }

    public int discussionSpeakerIndex() {
        return discussionSpeakerIndex;
    }

    public void discussionSpeakerIndex(int discussionSpeakerIndex) {
        this.discussionSpeakerIndex = discussionSpeakerIndex;
    }

    public int voteIndex() {
        return voteIndex;
    }

    public void voteIndex(int voteIndex) {
        this.voteIndex = voteIndex;
    }

    public int missionIndex() {
        return missionIndex;
    }

    public void missionIndex(int missionIndex) {
        this.missionIndex = missionIndex;
    }

    public Camp winnerCamp() {
        return winnerCamp;
    }

    public void winner(Camp winnerCamp) {
        this.winnerCamp = winnerCamp;
        this.updatedAt = Instant.now();
    }

    public void addApprovedMissionRound(int roundNo) {
        approvedMissionRounds.add(roundNo);
        updatedAt = Instant.now();
    }

    public void addFailedMissionRound(int roundNo) {
        failedMissionRounds.add(roundNo);
        updatedAt = Instant.now();
    }

    public void addCurrentProposalSeat(int seatNo) {
        currentProposalTeam.add(seatNo);
    }

    public void addCurrentMissionSeat(int seatNo) {
        currentMissionTeam.add(seatNo);
    }

    public void putVote(int seatNo, VoteChoice voteChoice) {
        currentVotes.put(seatNo, voteChoice);
    }

    public void putMissionChoice(int seatNo, MissionChoice missionChoice) {
        currentMissionChoices.put(seatNo, missionChoice);
    }

    public int playerCount() {
        return players.size();
    }

    public int nextSeatAfter(int seatNo) {
        if (players.isEmpty()) {
            return seatNo;
        }
        List<PlayerRegistration> ordered = players;
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).seatNo() == seatNo) {
                return ordered.get((index + 1) % ordered.size()).seatNo();
            }
        }
        return ordered.get(0).seatNo();
    }

    public int seatAtOffset(int seatNo, int offset) {
        if (players.isEmpty()) {
            return seatNo;
        }
        int index = 0;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).seatNo() == seatNo) {
                index = i;
                break;
            }
        }
        return players.get(Math.floorMod(index + offset, players.size())).seatNo();
    }

    public RoleAssignment roleAssignmentByPlayerId(String playerId) {
        return roleAssignmentsBySeat.values().stream()
                .filter(assignment -> assignment.playerId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown player: " + playerId));
    }

    public void ensureStatus(GameStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected status " + expected + " but was " + status);
        }
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public static GameRuntimeState restore(GameRuntimeStateSnapshot snapshot) {
        GameRuntimeState state = new GameRuntimeState(snapshot.setup());
        state.roleAssignmentsBySeat.clear();
        for (RoleAssignment roleAssignment : snapshot.roleAssignments()) {
            state.roleAssignmentsBySeat.put(roleAssignment.seatNo(), roleAssignment);
        }
        state.memoryByPlayerId.clear();
        snapshot.memoryByPlayerId().forEach((playerId, memory) -> state.memoryByPlayerId.put(playerId, new LinkedHashMap<>(memory)));
        state.resolvedLlmControllerConfigsByPlayerId.clear();
        snapshot.resolvedLlmControllerConfigsByPlayerId().forEach((playerId, config) ->
                state.resolvedLlmControllerConfigsByPlayerId.put(playerId, new LinkedHashMap<>(config)));
        state.events.clear();
        state.events.addAll(snapshot.events());
        state.auditEntries.clear();
        state.approvedMissionRounds.clear();
        state.approvedMissionRounds.addAll(snapshot.approvedMissionRounds());
        state.failedMissionRounds.clear();
        state.failedMissionRounds.addAll(snapshot.failedMissionRounds());
        state.currentProposalTeam.clear();
        state.currentProposalTeam.addAll(snapshot.currentProposalTeam());
        state.currentMissionTeam.clear();
        state.currentMissionTeam.addAll(snapshot.currentMissionTeam());
        state.currentVotes.clear();
        state.currentVotes.putAll(snapshot.currentVotes());
        state.currentMissionChoices.clear();
        state.currentMissionChoices.putAll(snapshot.currentMissionChoices());
        state.status = snapshot.status();
        state.phase = snapshot.phase();
        state.roundNo = snapshot.roundNo();
        state.currentLeaderSeat = snapshot.currentLeaderSeat();
        state.failedTeamVoteCount = snapshot.failedTeamVoteCount();
        state.discussionSpeakerIndex = snapshot.discussionSpeakerIndex();
        state.voteIndex = snapshot.voteIndex();
        state.missionIndex = snapshot.missionIndex();
        state.winnerCamp = snapshot.winnerCamp();
        state.sequence = snapshot.sequence();
        state.updatedAt = snapshot.updatedAt();
        return state;
    }
}
