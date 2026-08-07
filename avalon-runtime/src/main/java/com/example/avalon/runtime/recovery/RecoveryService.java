package com.example.avalon.runtime.recovery;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.persistence.model.GameEventRecord;
import com.example.avalon.persistence.model.GameSnapshotRecord;
import com.example.avalon.persistence.model.PlayerMemorySnapshotRecord;
import com.example.avalon.persistence.store.GameEventStore;
import com.example.avalon.persistence.store.GameSnapshotStore;
import com.example.avalon.persistence.store.PlayerMemorySnapshotStore;
import com.example.avalon.runtime.engine.ConfigDrivenGameRuleEngine;
import com.example.avalon.runtime.engine.GameRuleEngine;
import com.example.avalon.runtime.model.GameEvent;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.persistence.RuntimeStateCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecoveryService {
    private final GameSnapshotStore gameSnapshotStore;
    private final GameEventStore gameEventStore;
    private final PlayerMemorySnapshotStore playerMemorySnapshotStore;
    private final RuntimeStateCodec runtimeStateCodec;
    private final GameRuleEngine gameRuleEngine;
    private final ObjectMapper objectMapper;

    public RecoveryService(
            GameSnapshotStore gameSnapshotStore,
            GameEventStore gameEventStore,
            PlayerMemorySnapshotStore playerMemorySnapshotStore,
            RuntimeStateCodec runtimeStateCodec
    ) {
        this(gameSnapshotStore, gameEventStore, playerMemorySnapshotStore, runtimeStateCodec, new ConfigDrivenGameRuleEngine());
    }

    public RecoveryService(
            GameSnapshotStore gameSnapshotStore,
            GameEventStore gameEventStore,
            PlayerMemorySnapshotStore playerMemorySnapshotStore,
            RuntimeStateCodec runtimeStateCodec,
            GameRuleEngine gameRuleEngine
    ) {
        this.gameSnapshotStore = gameSnapshotStore;
        this.gameEventStore = gameEventStore;
        this.playerMemorySnapshotStore = playerMemorySnapshotStore;
        this.runtimeStateCodec = runtimeStateCodec;
        this.gameRuleEngine = gameRuleEngine;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public RecoveryResult recover(String gameId) {
        GameSnapshotRecord snapshot = gameSnapshotStore.findLatestByGameId(gameId)
                .orElseThrow(() -> new IllegalStateException("No snapshot found for game " + gameId));
        GameRuntimeState state = runtimeStateCodec.deserialize(snapshot.stateJson());
        List<GameEventRecord> eventsAfterSnapshot = gameEventStore.findByGameIdAfterSeqNo(gameId, snapshot.basedOnEventSeqNo()).stream()
                .sorted(Comparator.comparingLong(GameEventRecord::seqNo))
                .toList();
        List<PlayerMemorySnapshotRecord> restoredMemorySnapshots = new ArrayList<>();
        for (String playerId : state.memoryByPlayerId().keySet()) {
            playerMemorySnapshotStore.findLatestAtOrBefore(gameId, playerId, snapshot.basedOnEventSeqNo())
                    .ifPresent(memorySnapshot -> {
                        state.memoryByPlayerId().put(playerId, readMemory(memorySnapshot.memoryJson()));
                        restoredMemorySnapshots.add(memorySnapshot);
                    });
        }
        for (GameEventRecord eventRecord : eventsAfterSnapshot) {
            GameEvent event = toRuntimeEvent(eventRecord);
            applyEvent(state, event, readPayload(eventRecord.payloadJson()));
            state.appendRecoveredEvent(event);
        }
        return new RecoveryResult(state, snapshot, eventsAfterSnapshot, List.copyOf(restoredMemorySnapshots));
    }

    private void applyEvent(GameRuntimeState state, GameEvent event, Map<String, Object> payload) {
        switch (event.type()) {
            case "GAME_CREATED" -> {
                state.status(GameStatus.WAITING);
                state.phase(GamePhase.ROLE_REVEAL);
            }
            case "GAME_STARTED" -> {
                state.status(GameStatus.RUNNING);
                state.phase(GamePhase.DISCUSSION);
                state.roundNo(readInt(payload.getOrDefault("roundNo", 1), 1));
                state.currentLeaderSeat(readInt(payload.get("leaderSeat"), state.currentLeaderSeat()));
                state.resetRoundTurnState();
            }
            case "ROLE_ASSIGNED" -> state.putRoleAssignment(new RoleAssignment(
                    state.generatedGameId(),
                    event.actorId(),
                    readInt(payload.get("seatNo"), 0),
                    readString(payload.get("roleId")),
                    Camp.valueOf(readString(payload.get("camp"))),
                    new PlayerPrivateKnowledge(List.of(), readStringList(payload.get("privateKnowledge"))),
                    event.createdAt()));
            case "PLAYER_ACTION" -> {
                if (event.phase() == GamePhase.DISCUSSION) {
                    state.discussionSpeakerIndex(state.discussionSpeakerIndex() + 1);
                    if (state.discussionSpeakerIndex() >= state.playerCount()) {
                        state.discussionSpeakerIndex(0);
                    }
                }
            }
            case "TEAM_PROPOSED" -> {
                state.clearProposalState();
                readStringList(payload.get("playerIds")).stream()
                        .map(state::playerById)
                        .map(player -> player.seatNo())
                        .forEach(state::addCurrentProposalSeat);
                state.phase(GamePhase.TEAM_VOTE);
                state.voteIndex(0);
            }
            case "TEAM_VOTE_CAST" -> {
                int seatNo = state.playerById(event.actorId()).seatNo();
                VoteChoice voteChoice = VoteChoice.valueOf(readString(payload.get("vote")));
                state.putVote(seatNo, voteChoice);
                state.voteIndex(state.voteIndex() + 1);
                if (state.voteIndex() >= state.playerCount()) {
                    long approves = state.currentVotes().values().stream().filter(choice -> choice == VoteChoice.APPROVE).count();
                    long rejects = state.currentVotes().size() - approves;
                    if (approves > rejects) {
                        state.phase(GamePhase.MISSION_ACTION);
                        state.clearMissionState();
                        state.currentProposalTeam().forEach(state::addCurrentMissionSeat);
                    } else {
                        state.failedTeamVoteCount(state.failedTeamVoteCount() + 1);
                        state.clearProposalState();
                        state.currentLeaderSeat(state.nextSeatAfter(state.currentLeaderSeat()));
                        state.phase(GamePhase.DISCUSSION);
                        state.discussionSpeakerIndex(0);
                    }
                    state.voteIndex(0);
                }
            }
            case "TEAM_VOTE_REJECTED" -> {
            }
            case "MISSION_ACTION_CAST" -> {
                int seatNo = state.playerById(event.actorId()).seatNo();
                MissionChoice missionChoice = MissionChoice.valueOf(readString(payload.get("choice")));
                state.putMissionChoice(seatNo, missionChoice);
                state.missionIndex(state.missionIndex() + 1);
                if (state.missionIndex() >= state.currentMissionTeam().size()) {
                    state.phase(GamePhase.MISSION_RESOLUTION);
                }
            }
            case "MISSION_SUCCESS" -> resolveMissionOutcome(state, readInt(payload.get("roundNo"), state.roundNo()), false);
            case "MISSION_FAILED" -> resolveMissionOutcome(state, readInt(payload.get("roundNo"), state.roundNo()), true);
            case "ASSASSINATION_SUBMITTED" -> {
                String targetRole = readString(payload.get("targetRole"));
                String merlinRoleId = state.setup().ruleSetDefinition().assassinationRule().merlinRoleId();
                endGame(state, merlinRoleId.equals(targetRole) ? Camp.EVIL : Camp.GOOD);
            }
            case "GAME_ENDED" -> {
                state.winner(Camp.valueOf(readString(payload.get("winner"))));
                state.phase(GamePhase.GAME_END);
                state.status(GameStatus.ENDED);
            }
            case "GAME_PAUSED" -> state.status(GameStatus.PAUSED);
            default -> {
            }
        }
    }

    private void resolveMissionOutcome(GameRuntimeState state, int roundNo, boolean failed) {
        if (failed) {
            state.addFailedMissionRound(roundNo);
        } else {
            state.addApprovedMissionRound(roundNo);
        }
        state.clearProposalState();
        state.clearMissionState();
        if (gameRuleEngine.shouldEnterAssassination(state)) {
            state.phase(GamePhase.ASSASSINATION);
            return;
        }
        String winner = gameRuleEngine.resolveWinner(state);
        if (winner != null) {
            endGame(state, Camp.valueOf(winner));
            return;
        }
        state.roundNo(state.roundNo() + 1);
        state.currentLeaderSeat(state.nextSeatAfter(state.currentLeaderSeat()));
        state.phase(GamePhase.DISCUSSION);
        state.discussionSpeakerIndex(0);
    }

    private void endGame(GameRuntimeState state, Camp winner) {
        state.winner(winner);
        state.phase(GamePhase.GAME_END);
        state.status(GameStatus.ENDED);
    }

    private Map<String, Object> readMemory(String json) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
            return new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize player memory snapshot", e);
        }
    }

    private Map<String, Object> readPayload(String json) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
            return new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize event payload", e);
        }
    }

    private GameEvent toRuntimeEvent(GameEventRecord record) {
        return new GameEvent(
                record.seqNo(),
                record.type(),
                GamePhase.valueOf(record.phase()),
                record.actorPlayerId(),
                readPayload(record.payloadJson()),
                record.createdAt());
    }

    private int readInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(value.toString());
    }

    private String readString(Object value) {
        return value == null ? "" : value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        if (value instanceof String string && !string.isBlank()) {
            return List.of(string);
        }
        return List.of();
    }
}
