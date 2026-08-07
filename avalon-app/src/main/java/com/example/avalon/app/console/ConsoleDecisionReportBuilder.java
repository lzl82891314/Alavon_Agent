package com.example.avalon.app.console;

import com.example.avalon.api.dto.GameAuditEntryResponse;
import com.example.avalon.api.dto.GameEventEntryResponse;
import com.example.avalon.api.dto.GameStateResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ConsoleDecisionReportBuilder {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ConsoleDecisionReport build(GameStateResponse finalState,
                                       List<GameEventEntryResponse> events,
                                       List<GameAuditEntryResponse> auditEntries,
                                       List<ConsoleDecisionPlayer> players) {
        List<GameEventEntryResponse> orderedEvents = events == null
                ? List.of()
                : events.stream()
                .sorted(Comparator.comparingLong(event -> event.getSeqNo() == null ? Long.MAX_VALUE : event.getSeqNo()))
                .toList();
        List<ConsoleDecisionPlayer> orderedPlayers = players == null
                ? List.of()
                : players.stream()
                .sorted(Comparator.comparingInt(player -> player.seatNo() == null ? Integer.MAX_VALUE : player.seatNo()))
                .toList();
        Map<String, ConsoleDecisionPlayer> playersById = orderedPlayers.stream()
                .filter(player -> player.playerId() != null && !player.playerId().isBlank())
                .collect(Collectors.toMap(
                        ConsoleDecisionPlayer::playerId,
                        player -> player,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, GameAuditEntryResponse> auditsByEventSeqNo = indexAudits(auditEntries);
        LinkedHashMap<String, MutableSection> sections = new LinkedHashMap<>();
        Map<String, Deque<MutableRow>> pendingRows = new LinkedHashMap<>();

        boolean started = false;
        boolean advanceRoundOnNextRegularEvent = false;
        int currentRoundNo = 1;
        for (GameEventEntryResponse event : orderedEvents) {
            if ("GAME_STARTED".equals(event.getType())) {
                started = true;
                currentRoundNo = 1;
                advanceRoundOnNextRegularEvent = false;
                continue;
            }
            if (!started) {
                continue;
            }
            if (advanceRoundOnNextRegularEvent && belongsToNextRegularRound(event)) {
                currentRoundNo++;
                advanceRoundOnNextRegularEvent = false;
            }

            boolean assassinationSection = belongsToAssassination(event);
            if (assassinationSection) {
                advanceRoundOnNextRegularEvent = false;
            }
            MutableSection section = sectionFor(sections, assassinationSection, currentRoundNo);
            processEvent(event, section, auditsByEventSeqNo.get(event.getSeqNo()), pendingRows, playersById);

            if ("MISSION_SUCCESS".equals(event.getType()) || "MISSION_FAILED".equals(event.getType())) {
                advanceRoundOnNextRegularEvent = true;
            }
        }

        List<ConsoleDecisionSection> builtSections = sections.values().stream()
                .map(MutableSection::freeze)
                .toList();
        String winnerCamp = null;
        if (finalState != null && finalState.getPublicState() != null) {
            winnerCamp = stringValue(finalState.getPublicState().get("winnerCamp"));
        }
        return new ConsoleDecisionReport(
                finalState == null ? null : finalState.getGameId(),
                finalState == null ? null : finalState.getStatus(),
                finalState == null ? null : finalState.getPhase(),
                winnerCamp,
                finalState == null ? null : finalState.getRoundNo(),
                orderedPlayers,
                builtSections
        );
    }

    private Map<Long, GameAuditEntryResponse> indexAudits(List<GameAuditEntryResponse> auditEntries) {
        LinkedHashMap<Long, GameAuditEntryResponse> indexed = new LinkedHashMap<>();
        if (auditEntries == null) {
            return indexed;
        }
        auditEntries.stream()
                .filter(entry -> entry.getEventSeqNo() != null)
                .sorted(Comparator.comparingLong(GameAuditEntryResponse::getEventSeqNo))
                .forEach(entry -> indexed.put(entry.getEventSeqNo(), entry));
        return indexed;
    }

    private boolean belongsToAssassination(GameEventEntryResponse event) {
        return "ASSASSINATION".equals(event.getPhase())
                || "ASSASSINATION_SUBMITTED".equals(event.getType());
    }

    private boolean belongsToNextRegularRound(GameEventEntryResponse event) {
        if (belongsToAssassination(event)) {
            return false;
        }
        if ("GAME_PAUSED".equals(event.getType()) || "GAME_ENDED".equals(event.getType())) {
            return false;
        }
        return switch (Objects.toString(event.getPhase(), "")) {
            case "DISCUSSION", "TEAM_PROPOSAL", "TEAM_VOTE", "MISSION_ACTION" -> true;
            default -> false;
        };
    }

    private MutableSection sectionFor(LinkedHashMap<String, MutableSection> sections,
                                      boolean assassinationSection,
                                      int currentRoundNo) {
        String key = assassinationSection ? "ASSASSINATION" : "ROUND-" + currentRoundNo;
        return sections.computeIfAbsent(key, ignored -> assassinationSection
                ? new MutableSection(key, "终局：刺杀")
                : new MutableSection(key, "第" + currentRoundNo + "轮"));
    }

    private void processEvent(GameEventEntryResponse event,
                              MutableSection section,
                              GameAuditEntryResponse auditEntry,
                              Map<String, Deque<MutableRow>> pendingRows,
                              Map<String, ConsoleDecisionPlayer> playersById) {
        switch (Objects.toString(event.getType(), "")) {
            case "PLAYER_ACTION" -> handlePlayerAction(event, auditEntry, section, pendingRows, playersById);
            case "TEAM_PROPOSED" -> {
                section.leaderPlayerId = event.getActorId();
                section.teamPlayerIds = stringList(event.getPayload().get("playerIds"));
                enrichPendingRow(event.getActorId(), "TEAM_PROPOSAL", detailForProposal(event), pendingRows);
            }
            case "TEAM_VOTE_CAST" -> {
                section.votes.add(new ConsoleDecisionVote(event.getActorId(), stringValue(event.getPayload().get("vote"))));
                enrichPendingRow(event.getActorId(), "TEAM_VOTE", stringValue(event.getPayload().get("vote")), pendingRows);
            }
            case "TEAM_VOTE_REJECTED" -> section.voteRejected = true;
            case "MISSION_ACTION_CAST" -> enrichPendingRow(event.getActorId(), "MISSION_ACTION", stringValue(event.getPayload().get("choice")), pendingRows);
            case "MISSION_SUCCESS" -> {
                section.missionOutcome = "SUCCESS";
                section.missionFailCount = 0L;
            }
            case "MISSION_FAILED" -> {
                section.missionOutcome = "FAILED";
                section.missionFailCount = longValue(event.getPayload().get("fails"));
            }
            case "ASSASSINATION_SUBMITTED" -> enrichPendingRow(event.getActorId(), "ASSASSINATION", stringValue(event.getPayload().get("targetPlayerId")), pendingRows);
            case "GAME_PAUSED" -> handlePausedEvent(event, auditEntry, section, playersById);
            case "GAME_ENDED" -> section.winnerCamp = stringValue(event.getPayload().get("winner"));
            default -> {
            }
        }
    }

    private void handlePlayerAction(GameEventEntryResponse event,
                                    GameAuditEntryResponse auditEntry,
                                    MutableSection section,
                                    Map<String, Deque<MutableRow>> pendingRows,
                                    Map<String, ConsoleDecisionPlayer> playersById) {
        MutableRow row = new MutableRow();
        row.eventSeqNo = event.getSeqNo();
        row.phase = event.getPhase();
        row.playerId = event.getActorId();
        row.roleId = roleIdOf(event.getActorId(), playersById);
        row.actionType = stringValue(event.getPayload().get("actionType"));
        row.publicSpeech = normalizeText(stringValue(event.getPayload().get("speech")));
        if (row.publicSpeech == null) {
            row.publicSpeech = publicSpeech(auditEntry);
        }
        row.privateThought = privateThought(auditEntry);
        row.note = buildNote(auditEntry, false);
        row.failed = false;
        row.actionDetail = detailFromAudit(auditEntry, row.actionType);
        section.rows.add(row);

        if (needsFollowupDetail(row.actionType) && row.actionDetail == null) {
            pendingRows.computeIfAbsent(detailKey(row.playerId, row.actionType), ignored -> new ArrayDeque<>()).addLast(row);
        }
    }

    private void handlePausedEvent(GameEventEntryResponse event,
                                   GameAuditEntryResponse auditEntry,
                                   MutableSection section,
                                   Map<String, ConsoleDecisionPlayer> playersById) {
        section.pauseReason = stringValue(event.getPayload().get("reason"));

        MutableRow row = new MutableRow();
        row.eventSeqNo = event.getSeqNo();
        row.phase = event.getPhase();
        row.playerId = stringValue(event.getPayload().get("playerId"));
        row.roleId = roleIdOf(row.playerId, playersById);
        row.actionType = actionTypeForPause(event, auditEntry);
        row.actionDetail = detailFromAudit(auditEntry, row.actionType);
        row.publicSpeech = publicSpeech(auditEntry);
        row.privateThought = privateThought(auditEntry);
        row.note = buildNote(auditEntry, true);
        row.failed = true;
        section.rows.add(row);
    }

    private String roleIdOf(String playerId, Map<String, ConsoleDecisionPlayer> playersById) {
        ConsoleDecisionPlayer player = playersById.get(playerId);
        return player == null ? null : player.roleId();
    }

    private String actionTypeForPause(GameEventEntryResponse event, GameAuditEntryResponse auditEntry) {
        Map<String, Object> parsedAction = structuredMap(auditEntry == null ? null : auditEntry.getParsedActionJson());
        String actionType = stringValue(parsedAction.get("actionType"));
        if (actionType != null) {
            return actionType;
        }
        return switch (Objects.toString(event.getPhase(), "")) {
            case "DISCUSSION" -> "PUBLIC_SPEECH";
            case "TEAM_PROPOSAL" -> "TEAM_PROPOSAL";
            case "TEAM_VOTE" -> "TEAM_VOTE";
            case "MISSION_ACTION" -> "MISSION_ACTION";
            case "ASSASSINATION" -> "ASSASSINATION";
            default -> null;
        };
    }

    private boolean needsFollowupDetail(String actionType) {
        return switch (Objects.toString(actionType, "")) {
            case "TEAM_PROPOSAL", "TEAM_VOTE", "MISSION_ACTION", "ASSASSINATION" -> true;
            default -> false;
        };
    }

    private void enrichPendingRow(String playerId,
                                  String actionType,
                                  String actionDetail,
                                  Map<String, Deque<MutableRow>> pendingRows) {
        Deque<MutableRow> queue = pendingRows.get(detailKey(playerId, actionType));
        if (queue == null || queue.isEmpty()) {
            return;
        }
        MutableRow row = queue.removeFirst();
        row.actionDetail = normalizeText(actionDetail);
        if (queue.isEmpty()) {
            pendingRows.remove(detailKey(playerId, actionType));
        }
    }

    private String detailKey(String playerId, String actionType) {
        return Objects.toString(playerId, "") + "|" + Objects.toString(actionType, "");
    }

    private String detailForProposal(GameEventEntryResponse event) {
        List<String> playerIds = stringList(event.getPayload().get("playerIds"));
        return playerIds.isEmpty() ? null : playerIds.toString();
    }

    private String detailFromAudit(GameAuditEntryResponse auditEntry, String actionType) {
        Map<String, Object> parsedAction = structuredMap(auditEntry == null ? null : auditEntry.getParsedActionJson());
        if (parsedAction.isEmpty()) {
            return null;
        }
        return switch (Objects.toString(actionType, "")) {
            case "TEAM_PROPOSAL" -> {
                List<String> selectedPlayerIds = stringList(parsedAction.get("selectedPlayerIds"));
                yield selectedPlayerIds.isEmpty() ? null : selectedPlayerIds.toString();
            }
            case "TEAM_VOTE" -> stringValue(parsedAction.get("vote"));
            case "MISSION_ACTION" -> stringValue(parsedAction.get("choice"));
            case "ASSASSINATION" -> stringValue(parsedAction.get("targetPlayerId"));
            case "PUBLIC_SPEECH" -> null;
            default -> null;
        };
    }

    private String publicSpeech(GameAuditEntryResponse auditEntry) {
        Map<String, Object> rawModelResponse = structuredMap(auditEntry == null ? null : auditEntry.getRawModelResponseJson());
        return normalizeText(stringValue(rawModelResponse.get("publicSpeech")));
    }

    private String privateThought(GameAuditEntryResponse auditEntry) {
        Map<String, Object> rawModelResponse = structuredMap(auditEntry == null ? null : auditEntry.getRawModelResponseJson());
        String privateThought = normalizeText(stringValue(rawModelResponse.get("privateThought")));
        if (privateThought != null) {
            return privateThought;
        }
        String reasoningPreview = normalizeText(stringValue(rawModelResponse.get("reasoningDetailsPreview")));
        if (reasoningPreview != null) {
            return reasoningPreview;
        }
        Map<String, Object> auditReason = structuredMap(auditEntry == null ? null : auditEntry.getAuditReasonJson());
        Object reasonSummary = auditReason.get("reasonSummary");
        if (reasonSummary instanceof Collection<?> collection && !collection.isEmpty()) {
            return collection.stream()
                    .map(String::valueOf)
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining("；"));
        }
        return normalizeText(stringValue(auditReason.get("goal")));
    }

    private String buildNote(GameAuditEntryResponse auditEntry, boolean includeErrorMessage) {
        if (auditEntry == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        String inputKnowledgeSummary = inputPrivateKnowledgeSummary(auditEntry);
        if (inputKnowledgeSummary != null) {
            parts.add("模型私有知识: " + inputKnowledgeSummary);
        }
        Map<String, Object> validation = structuredMap(auditEntry.getValidationResultJson());
        Object warnings = validation.get("optionalSectionWarnings");
        if (warnings instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (!(item instanceof Map<?, ?> warning)) {
                    continue;
                }
                String field = stringValue(warning.get("field"));
                String reason = stringValue(warning.get("reason"));
                String preview = normalizeText(stringValue(warning.get("contentPreview")));
                StringBuilder builder = new StringBuilder();
                builder.append(field == null ? "附加字段" : field);
                if (reason != null) {
                    builder.append("：").append(optionalSectionReasonLabel(reason));
                }
                if (preview != null) {
                    builder.append("（").append(preview).append("）");
                }
                parts.add(builder.toString());
            }
        }
        String transportSummary = transportFailureSummary(auditEntry);
        if (transportSummary != null) {
            parts.add("传输诊断: " + transportSummary);
        }
        if (includeErrorMessage) {
            String error = normalizeText(stringValue(validation.get("errorMessage")));
            if (error == null) {
                error = normalizeText(auditEntry.getErrorMessage());
            }
            if (error != null) {
                parts.add(error);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("；", parts);
    }

    private String transportFailureSummary(GameAuditEntryResponse auditEntry) {
        Map<String, Object> rawModelResponse = structuredMap(auditEntry == null ? null : auditEntry.getRawModelResponseJson());
        String failureDomain = stringValue(rawModelResponse.get("failureDomain"));
        if (!"transport".equalsIgnoreCase(failureDomain)) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        String failureKind = stringValue(rawModelResponse.get("failureKind"));
        if (failureKind != null) {
            parts.add("类型=" + failureKind);
        }
        Object attempts = rawModelResponse.get("transportAttempts");
        if (attempts != null) {
            parts.add("尝试=" + attempts);
        }
        Object timeoutMs = rawModelResponse.get("timeoutMs");
        if (timeoutMs != null) {
            parts.add("超时=" + timeoutMs + "ms");
        }
        Object statusCode = rawModelResponse.get("statusCode");
        if (statusCode != null) {
            parts.add("状态=" + statusCode);
        }
        String rootClass = normalizeText(stringValue(rawModelResponse.get("rootExceptionClass")));
        if (rootClass != null) {
            parts.add("异常=" + rootClass);
        }
        String rootMessage = normalizeText(stringValue(rawModelResponse.get("rootExceptionMessage")));
        if (rootMessage != null) {
            parts.add("原因=" + rootMessage);
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private String inputPrivateKnowledgeSummary(GameAuditEntryResponse auditEntry) {
        Map<String, Object> inputContext = structuredMap(auditEntry.getInputContextJson());
        Object rawPrivateKnowledge = inputContext.get("privateKnowledge");
        if (!(rawPrivateKnowledge instanceof Map<?, ?> privateKnowledge)) {
            return null;
        }
        Map<String, Object> copied = new LinkedHashMap<>();
        privateKnowledge.forEach((key, value) -> copied.put(String.valueOf(key), value));
        String summary = ConsoleKnowledgeFormatter.summarize(copied);
        return "无".equals(summary) ? null : summary;
    }

    private String optionalSectionReasonLabel(String reason) {
        return switch (Objects.toString(reason, "")) {
            case "expected_json_object" -> "不是 JSON 对象";
            case "dto_conversion_failed" -> "对象字段形状不合法";
            default -> reason;
        };
    }

    private Map<String, Object> structuredMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .map(this::stringValue)
                .filter(Objects::nonNull)
                .toList();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Long.parseLong(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r", " ").replace("\n", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static final class MutableSection {
        private final String key;
        private final String title;
        private String leaderPlayerId;
        private List<String> teamPlayerIds = List.of();
        private final List<ConsoleDecisionVote> votes = new ArrayList<>();
        private boolean voteRejected;
        private String missionOutcome;
        private Long missionFailCount;
        private String pauseReason;
        private String winnerCamp;
        private final List<MutableRow> rows = new ArrayList<>();

        private MutableSection(String key, String title) {
            this.key = key;
            this.title = title;
        }

        private ConsoleDecisionSection freeze() {
            return new ConsoleDecisionSection(
                    key,
                    title,
                    leaderPlayerId,
                    teamPlayerIds,
                    votes,
                    voteRejected,
                    missionOutcome,
                    missionFailCount,
                    pauseReason,
                    winnerCamp,
                    rows.stream().map(MutableRow::freeze).toList()
            );
        }
    }

    private static final class MutableRow {
        private Long eventSeqNo;
        private String phase;
        private String playerId;
        private String roleId;
        private String actionType;
        private String actionDetail;
        private String publicSpeech;
        private String privateThought;
        private String note;
        private boolean failed;

        private ConsoleDecisionRow freeze() {
            return new ConsoleDecisionRow(
                    eventSeqNo,
                    phase,
                    playerId,
                    roleId,
                    actionType,
                    actionDetail,
                    publicSpeech,
                    privateThought,
                    note,
                    failed
            );
        }
    }
}
