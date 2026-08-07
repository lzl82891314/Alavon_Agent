package com.example.avalon.runtime.recovery;

import com.example.avalon.persistence.model.AuditRecord;
import com.example.avalon.persistence.model.GameEventRecord;
import com.example.avalon.persistence.store.AuditRecordStore;
import com.example.avalon.persistence.store.GameEventStore;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReplayQueryService {
    private final GameEventStore gameEventStore;
    private final AuditRecordStore auditRecordStore;

    public ReplayQueryService(GameEventStore gameEventStore, AuditRecordStore auditRecordStore) {
        this.gameEventStore = gameEventStore;
        this.auditRecordStore = auditRecordStore;
    }

    public List<GameEventRecord> events(String gameId) {
        return gameEventStore.findByGameId(gameId);
    }

    public List<ReplayProjectionStep> replay(String gameId) {
        return gameEventStore.findByGameId(gameId).stream()
                .sorted(Comparator.comparingLong(GameEventRecord::seqNo))
                .map(this::toReplayStep)
                .toList();
    }

    public List<AuditRecord> audit(String gameId) {
        return auditRecordStore.findByGameId(gameId);
    }

    private ReplayProjectionStep toReplayStep(GameEventRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", record.type());
        payload.put("actorId", record.actorPlayerId());
        payload.put("phase", record.phase());
        payload.put("sourceVisibility", record.visibility());
        payload.put("replaySummary", summarize(record));
        payload.put("sourceSeqNo", record.seqNo());
        return new ReplayProjectionStep(
                record.seqNo(),
                record.type(),
                record.phase(),
                record.actorPlayerId(),
                replayKind(record),
                summarize(record),
                payload,
                record.createdAt() == null ? Instant.now() : record.createdAt());
    }

    private String replayKind(GameEventRecord record) {
        return switch (record.type()) {
            case "GAME_CREATED" -> "GAME_STARTUP";
            case "GAME_STARTED" -> "ROUND_OPENING";
            case "ROLE_ASSIGNED" -> "ROLE_ASSIGNMENT";
            case "PLAYER_ACTION" -> "PLAYER_DECISION";
            case "TEAM_PROPOSED" -> "TEAM_FORMED";
            case "TEAM_VOTE_CAST" -> "VOTE_RECORDED";
            case "TEAM_VOTE_REJECTED" -> "VOTE_RESULT";
            case "MISSION_ACTION_CAST" -> "MISSION_DECISION";
            case "MISSION_SUCCESS", "MISSION_FAILED" -> "MISSION_RESULT";
            case "ASSASSINATION_SUBMITTED" -> "ASSASSINATION_RESULT";
            case "GAME_PAUSED" -> "RUN_PAUSED";
            case "GAME_ENDED" -> "GAME_FINAL";
            default -> "EVENT";
        };
    }

    private String summarize(GameEventRecord record) {
        Map<String, Object> payload = parsePayload(record.payloadJson());
        return switch (record.type()) {
            case "GAME_CREATED" -> "游戏已创建";
            case "GAME_STARTED" -> "游戏开始，首位队长为 " + payload.getOrDefault("leaderSeat", "?") + " 号位";
            case "ROLE_ASSIGNED" -> payload.getOrDefault("seatNo", "?") + " 号位分配到身份 " + payload.getOrDefault("roleId", "?");
            case "PLAYER_ACTION" -> "玩家 " + record.actorPlayerId() + " 执行动作 " + payload.getOrDefault("actionType", "ACTION");
            case "TEAM_PROPOSED" -> "队长 " + record.actorPlayerId() + " 提议队伍 " + payload.getOrDefault("playerIds", List.of());
            case "TEAM_VOTE_CAST" -> "玩家 " + record.actorPlayerId() + " 投票 " + payload.getOrDefault("vote", "?");
            case "TEAM_VOTE_REJECTED" -> "队伍被否决，累计否决次数 " + payload.getOrDefault("failedTeamVoteCount", "?");
            case "MISSION_ACTION_CAST" -> "玩家 " + record.actorPlayerId() + " 提交任务选择 " + payload.getOrDefault("choice", "?");
            case "MISSION_SUCCESS" -> "第 " + payload.getOrDefault("roundNo", "?") + " 轮任务成功";
            case "MISSION_FAILED" -> "第 " + payload.getOrDefault("roundNo", "?") + " 轮任务失败，失败票数 " + payload.getOrDefault("fails", "?");
            case "ASSASSINATION_SUBMITTED" -> "刺客选择目标 " + payload.getOrDefault("targetPlayerId", "?");
            case "GAME_PAUSED" -> "游戏因 " + payload.getOrDefault("reason", "?") + " 暂停，玩家 " + payload.getOrDefault("playerId", "?");
            case "GAME_ENDED" -> "游戏结束，胜利方 " + payload.getOrDefault("winner", "?");
            default -> "回放步骤：" + record.type();
        };
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }
}
