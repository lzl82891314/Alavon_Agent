package com.example.avalon.app.console;

import com.example.avalon.api.dto.GameAuditEntryResponse;
import com.example.avalon.api.dto.GameEventEntryResponse;
import com.example.avalon.api.dto.GameStateResponse;
import com.example.avalon.api.dto.ModelProfileProbeCheckResponse;
import com.example.avalon.api.dto.ModelProfileProbeResponse;
import com.example.avalon.api.dto.PlayerPrivateViewResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ConsoleTranscriptPrinter {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public String banner() {
        return """
                Avalon AI 控制台模式
                - 默认启动进入交互式控制台
                - 输入 `new` 创建一局新游戏
                - 输入 `run` 以慢速叙事方式播放整局
                - 输入 `help` 查看全部命令
                """;
    }

    public String helpText() {
        return """
                命令
                  new            通过控制台向导创建新游戏
                  use <gameId>   绑定到一个已持久化的游戏 ID
                  config         查看当前席位与控制器配置
                  start          启动当前游戏
                  step           执行一个运行时步骤并打印新增信息
                  run            如有需要先启动，再以慢速播放方式运行到结束或暂停
                  report         输出局后决策表，并写入 Markdown 报告
                  state          查看当前公开局面
                  players        查看全部玩家的私有视角
                  player <id>    查看指定玩家的私有视角，例如 `player P1`
                  events         查看原始事件流
                  replay         查看面向阅读的回放投影
                  audit          查看持久化审计记录
                  probe-model    测试指定 model profile 的连通性与结构化兼容性
                  help           显示本帮助
                  exit           退出控制台

                说明
                  `new` 向导默认支持 5 到 10 人标准阿瓦隆。
                  局后报告默认写入 `target/reports/avalon/<gameId>-decision-report.md`。
                  V1 仍不支持真人实时提交动作。
                  模型池 LLM 席位默认按座位绑定静态或托管 model profile。
                  如果想离线演示，可选择 `noop` 使用确定性回退策略。
                """;
    }

    public String formatConfig(ConsoleGameSession session) {
        if (session.seats().isEmpty()) {
            return "当前活动游戏没有本地配置快照。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("当前配置");
        builder.append(System.lineSeparator())
                .append("  随机种子=")
                .append(session.seed() == null ? "-" : session.seed());
        for (ConsoleGameSession.SeatDescriptor seat : session.seats()) {
            builder.append(System.lineSeparator())
                    .append("  ")
                    .append(seat.summary());
        }
        if (session.llmSelectionSummary() != null) {
            builder.append(System.lineSeparator())
                    .append("  模型选择模式=")
                    .append(selectionModeLabel(session.llmSelectionSummary()));
            for (String detail : session.llmSelectionDetails()) {
                builder.append(System.lineSeparator())
                        .append("    ")
                        .append(detail);
            }
        }
        return builder.toString();
    }

    public String formatState(GameStateResponse state, ConsoleGameSession session) {
        Map<String, Object> publicState = state.getPublicState() == null
                ? Map.of()
                : new LinkedHashMap<>(state.getPublicState());
        Object leaderSeat = publicState.get("leaderSeat");
        Object currentProposalTeam = publicState.get("currentProposalTeam");
        Object approvedMissionRounds = publicState.get("approvedMissionRounds");
        Object failedMissionRounds = publicState.get("failedMissionRounds");
        Object winnerCamp = publicState.get("winnerCamp");

        StringBuilder builder = new StringBuilder();
        builder.append("游戏 ").append(state.getGameId()).append(System.lineSeparator());
        builder.append("  状态=").append(statusLabel(state.getStatus()))
                .append(" 阶段=").append(phaseLabel(state.getPhase()))
                .append(" 轮次=第").append(state.getRoundNo()).append("轮")
                .append(System.lineSeparator());
        builder.append("  队长=").append(session.labelForSeat(leaderSeat))
                .append(" 连续否决=").append(Objects.toString(publicState.get("failedTeamVoteCount"), "0"))
                .append(System.lineSeparator());
        builder.append("  当前提案队伍=").append(formatSeatCollection(currentProposalTeam, session))
                .append(System.lineSeparator());
        builder.append("  成功任务=").append(formatSimpleCollection(approvedMissionRounds))
                .append(" 失败任务=").append(formatSimpleCollection(failedMissionRounds))
                .append(System.lineSeparator());
        builder.append("  胜利阵营=").append(campLabel(winnerCamp));
        if (state.getNextRequiredActor() != null || state.getWaitingReason() != null) {
            builder.append(System.lineSeparator())
                    .append("  下一位行动=").append(session.labelForPlayer(state.getNextRequiredActor()))
                    .append(" 等待原因=").append(Objects.toString(state.getWaitingReason(), "-"));
        }
        return builder.toString();
    }

    public String formatTurnLeadIn(GameStateResponse state, ConsoleGameSession session) {
        return ">>> 第%s轮｜%s｜即将行动：%s｜%s".formatted(
                state.getRoundNo(),
                phaseLabel(state.getPhase()),
                session.labelForPlayer(state.getNextRequiredActor()),
                Objects.toString(state.getWaitingReason(), "准备执行")
        );
    }

    public String formatEvent(GameEventEntryResponse event, ConsoleGameSession session) {
        StringBuilder builder = new StringBuilder();
        builder.append("[#").append(event.getSeqNo()).append("] ")
                .append(eventTypeLabel(event.getType()))
                .append(" | 阶段=").append(phaseLabel(event.getPhase()))
                .append(" | 行动者=").append(session.labelForPlayer(event.getActorId()));

        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        switch (event.getType()) {
            case "GAME_CREATED" -> builder.append(System.lineSeparator())
                    .append("  已创建游戏：").append(payload.getOrDefault("gameId", "?"));
            case "GAME_STARTED" -> builder.append(System.lineSeparator())
                    .append("  游戏开始，首位队长为 ").append(session.labelForSeat(payload.get("leaderSeat")))
                    .append("，玩家数=").append(payload.getOrDefault("playerCount", "?"));
            case "ROLE_ASSIGNED" -> {
                builder.append(System.lineSeparator())
                        .append("  ")
                        .append(session.labelForSeat(payload.get("seatNo")))
                        .append(" 身份=").append(roleLabel(payload.get("roleId")))
                        .append(" 阵营=").append(campLabel(payload.get("camp")));
                Object notes = payload.get("privateKnowledge");
                if (notes instanceof Collection<?> collection && !collection.isEmpty()) {
                    builder.append(System.lineSeparator())
                            .append("  私有情报=").append(formatSimpleCollection(collection));
                }
            }
            case "PLAYER_ACTION" -> {
                builder.append(System.lineSeparator())
                        .append("  座位=").append(session.labelForSeat(payload.get("seatNo")))
                        .append(" 动作=").append(actionTypeLabel(payload.get("actionType")));
                String speech = stringValue(payload.get("speech"));
                if (speech != null) {
                    builder.append(System.lineSeparator()).append("  公开发言=").append(speech);
                }
            }
            case "TEAM_PROPOSED" -> builder.append(System.lineSeparator())
                    .append("  提议队伍=").append(formatPlayerIdCollection(payload.get("playerIds"), session));
            case "TEAM_VOTE_CAST" -> builder.append(System.lineSeparator())
                    .append("  投票=").append(voteLabel(payload.get("vote")));
            case "TEAM_VOTE_REJECTED" -> builder.append(System.lineSeparator())
                    .append("  队伍被否决，累计否决次数=")
                    .append(payload.getOrDefault("failedTeamVoteCount", "?"));
            case "MISSION_ACTION_CAST" -> builder.append(System.lineSeparator())
                    .append("  任务选择=").append(missionChoiceLabel(payload.get("choice")));
            case "MISSION_SUCCESS" -> builder.append(System.lineSeparator())
                    .append("  第").append(payload.getOrDefault("roundNo", "?")).append("轮任务成功");
            case "MISSION_FAILED" -> builder.append(System.lineSeparator())
                    .append("  第").append(payload.getOrDefault("roundNo", "?")).append("轮任务失败，失败票数=")
                    .append(payload.getOrDefault("fails", "?"));
            case "ASSASSINATION_SUBMITTED" -> builder.append(System.lineSeparator())
                    .append("  刺杀目标=").append(session.labelForPlayer(stringValue(payload.get("targetPlayerId"))))
                    .append("，目标身份=").append(roleLabel(payload.get("targetRole")));
            case "GAME_PAUSED" -> builder.append(System.lineSeparator())
                    .append("  游戏暂停，原因=").append(pauseReasonLabel(payload.get("reason")))
                    .append("，玩家=").append(session.labelForPlayer(stringValue(payload.get("playerId"))));
            case "GAME_ENDED" -> builder.append(System.lineSeparator())
                    .append("  游戏结束，胜利阵营=").append(campLabel(payload.get("winner")));
            default -> {
                if (!payload.isEmpty()) {
                    builder.append(System.lineSeparator()).append("  载荷=").append(formatJson(payload));
                }
            }
        }
        return builder.toString();
    }

    public String formatReplayStep(GameEventEntryResponse step, ConsoleGameSession session) {
        return "[#" + step.getSeqNo() + "] "
                + replayKindLabel(step.getReplayKind())
                + " | "
                + Objects.toString(step.getSummary(), eventTypeLabel(step.getType()))
                + " | 行动者="
                + session.labelForPlayer(step.getActorId());
    }

    public String formatAuditEntry(GameAuditEntryResponse entry) {
        StringBuilder builder = new StringBuilder();
        builder.append("[审计 ").append(entry.getAuditId()).append("]")
                .append(" 事件序号=").append(entry.getEventSeqNo())
                .append(" 玩家=").append(entry.getPlayerId())
                .append(" 可见性=").append(entry.getVisibility());
        String privateThought = privateThought(entry);
        Map<String, Object> validation = structuredMap(entry.getValidationResultJson());
        if (privateThought != null) {
            builder.append(System.lineSeparator()).append("  私有思考=").append(privateThought);
        }
        appendResponseDiagnostics(builder, structuredMap(entry.getRawModelResponseJson()), privateThought);
        appendOptionalSectionWarnings(builder, validation);
        String detailedError = detailedError(entry);
        if (detailedError != null) {
            builder.append(System.lineSeparator()).append("  错误=").append(detailedError);
        }
        appendJsonBlock(builder, "模型原始响应", entry.getRawModelResponseJson());
        appendJsonBlock(builder, "解析动作", entry.getParsedActionJson());
        appendJsonBlock(builder, "决策依据", entry.getAuditReasonJson());
        appendJsonBlock(builder, "校验结果", entry.getValidationResultJson());
        return builder.toString();
    }

    public String formatInlineThought(GameAuditEntryResponse entry, ConsoleGameSession session) {
        StringBuilder builder = new StringBuilder();
        builder.append("[思考] ").append(session.labelForPlayer(entry.getPlayerId()));
        String privateThought = privateThought(entry);
        Map<String, Object> validation = structuredMap(entry.getValidationResultJson());
        builder.append(System.lineSeparator())
                .append("  私有思考=").append(privateThought == null ? "未提供私有思考" : privateThought);
        appendResponseDiagnostics(builder, structuredMap(entry.getRawModelResponseJson()), privateThought);
        appendOptionalSectionWarnings(builder, validation);
        String reasonSummary = reasonSummary(entry.getAuditReasonJson());
        if (reasonSummary != null) {
            builder.append(System.lineSeparator())
                    .append("  决策依据=").append(reasonSummary);
        }
        String parsedAction = compactJson(entry.getParsedActionJson());
        if (parsedAction != null) {
            builder.append(System.lineSeparator())
                    .append("  结构化动作=").append(parsedAction);
        }
        String detailedError = detailedError(entry);
        if (detailedError != null) {
            builder.append(System.lineSeparator())
                    .append("  错误=").append(detailedError);
        }
        return builder.toString();
    }

    public String formatModelProbe(ModelProfileProbeResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append("模型探测 ").append(Objects.toString(response.getModelId(), "-"))
                .append(System.lineSeparator())
                .append("  Provider=").append(Objects.toString(response.getProvider(), "-"))
                .append(" Model=").append(Objects.toString(response.getModelName(), "-"))
                .append(System.lineSeparator())
                .append("  BaseUrl=").append(Objects.toString(response.getBaseUrl(), "-"))
                .append(" 诊断=").append(diagnosisLabel(response.getDiagnosis()));
        if (response.getReachable() != null) {
            builder.append(System.lineSeparator())
                    .append("  连通性=").append(booleanLabel(response.getReachable()));
        }
        if (response.getStructuredCompatible() != null) {
            builder.append(System.lineSeparator())
                    .append("  结构化兼容=").append(booleanLabel(response.getStructuredCompatible()));
        }
        for (ModelProfileProbeCheckResponse check : response.getChecks()) {
            builder.append(System.lineSeparator())
                    .append("  [").append(probeCheckLabel(check.getCheckType())).append("] ")
                    .append(booleanLabel(check.isSuccess()));
            if (check.getHttpStatus() != null) {
                builder.append(" HTTP=").append(check.getHttpStatus());
            }
            if (check.getLatencyMs() != null) {
                builder.append(" 耗时=").append(check.getLatencyMs()).append("ms");
            }
            if (check.getFinishReason() != null && !check.getFinishReason().isBlank()) {
                builder.append(" 结束原因=").append(check.getFinishReason());
            }
            if (check.getContentShape() != null && !check.getContentShape().isBlank()) {
                builder.append(System.lineSeparator())
                        .append("    内容形态=").append(contentShapeLabel(check.getContentShape()));
            }
            if (check.getContentPresent() != null) {
                builder.append(System.lineSeparator())
                        .append("    content=").append(booleanFlag(check.getContentPresent()));
            }
            if (check.getReasoningDetailsPresent() != null) {
                builder.append(System.lineSeparator())
                        .append("    reasoning_details=").append(booleanFlag(check.getReasoningDetailsPresent()));
            }
            if (check.getAssistantPreview() != null && !check.getAssistantPreview().isBlank()) {
                builder.append(System.lineSeparator())
                        .append("    响应预览=").append(check.getAssistantPreview());
            }
            if (check.getReasoningDetailsPreview() != null && !check.getReasoningDetailsPreview().isBlank()) {
                builder.append(System.lineSeparator())
                        .append("    推理预览=").append(check.getReasoningDetailsPreview());
            }
            if (check.getErrorMessage() != null && !check.getErrorMessage().isBlank()) {
                builder.append(System.lineSeparator())
                        .append("    错误=").append(check.getErrorMessage());
            }
        }
        return builder.toString();
    }

    public String formatPlayerView(String playerId, PlayerPrivateViewResponse view, ConsoleGameSession session) {
        StringBuilder builder = new StringBuilder();
        builder.append("玩家视角：")
                .append(session.labelForPlayer(playerId))
                .append(System.lineSeparator());
        builder.append("  座位=").append(view.getSeatNo())
                .append(" 身份=").append(roleLabel(view.getRoleSummary()))
                .append(System.lineSeparator());
        builder.append("  允许动作=").append(formatActionCollection(view.getAllowedActions()))
                .append(System.lineSeparator());
        builder.append("  私有情报=").append(formatJson(view.getPrivateKnowledge()))
                .append(System.lineSeparator());
        builder.append("  记忆快照=").append(formatJson(view.getMemorySnapshot()));
        return builder.toString();
    }

    public String formatDecisionReport(ConsoleDecisionReport report, ConsoleGameSession session, Path markdownPath) {
        Map<String, ConsoleDecisionPlayer> playersById = reportPlayersById(report);
        StringBuilder builder = new StringBuilder();
        builder.append("局后决策报告 ").append(Objects.toString(report.gameId(), "-"))
                .append(System.lineSeparator())
                .append("  状态=").append(statusLabel(report.status()))
                .append(" 阶段=").append(phaseLabel(report.phase()))
                .append(" 轮次=").append(report.roundNo() == null ? "-" : "第" + report.roundNo() + "轮")
                .append(System.lineSeparator())
                .append("  胜利阵营=").append(campLabel(report.winnerCamp()))
                .append(System.lineSeparator())
                .append("  Markdown=").append(markdownPath.toAbsolutePath().normalize())
                .append(System.lineSeparator())
                .append("  说明=privateThought 是模型原始文本，不等同于规则允许的确定知识");

        if (!report.players().isEmpty()) {
            builder.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append("角色总表")
                    .append(System.lineSeparator())
                    .append(renderMarkdownTable(
                            List.of("座位", "玩家", "角色", "阵营", "初始私有知识"),
                            report.players().stream()
                                    .map(player -> List.of(
                                            Objects.toString(player.seatNo(), "-"),
                                            limitConsoleCell(reportPlayerLabel(playersById, session, player.playerId()), 14),
                                            limitConsoleCell(roleLabel(player.roleId()), 10),
                                            limitConsoleCell(campLabel(player.camp()), 10),
                                            limitConsoleCell(orDash(player.privateKnowledgeSummary()), 48)
                                    ))
                                    .toList()
                    ));
        }

        for (ConsoleDecisionSection section : report.sections()) {
            builder.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append(section.title());
            String summary = reportSectionSummary(section, session, playersById);
            if (summary != null) {
                builder.append(System.lineSeparator())
                        .append("  摘要=").append(summary);
            }
            if (section.rows().isEmpty()) {
                builder.append(System.lineSeparator())
                        .append("  本节没有可展示的 agent 决策。");
                continue;
            }
            builder.append(System.lineSeparator())
                    .append(renderMarkdownTable(
                            List.of("序号", "阶段", "玩家", "角色", "动作", "公开发言", "私有思考", "备注"),
                            section.rows().stream()
                                    .map(row -> List.of(
                                            Objects.toString(row.eventSeqNo(), "-"),
                                            limitConsoleCell(phaseLabel(row.phase()), 10),
                                            limitConsoleCell(reportPlayerLabel(playersById, session, row.playerId()), 14),
                                            limitConsoleCell(roleLabel(row.roleId()), 10),
                                            limitConsoleCell(reportActionLabel(row, session, playersById), 20),
                                            limitConsoleCell(orDash(row.publicSpeech()), 24),
                                            limitConsoleCell(orDash(row.privateThought()), 24),
                                            limitConsoleCell(orDash(row.note()), 24)
                                    ))
                                    .toList()
                    ));
        }
        return builder.toString();
    }

    public String formatDecisionReportMarkdown(ConsoleDecisionReport report, ConsoleGameSession session) {
        Map<String, ConsoleDecisionPlayer> playersById = reportPlayersById(report);
        StringBuilder builder = new StringBuilder();
        builder.append("# 决策报告：").append(Objects.toString(report.gameId(), "-"))
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("- 状态：").append(statusLabel(report.status())).append(System.lineSeparator())
                .append("- 阶段：").append(phaseLabel(report.phase())).append(System.lineSeparator())
                .append("- 当前轮次：").append(report.roundNo() == null ? "-" : "第" + report.roundNo() + "轮").append(System.lineSeparator())
                .append("- 胜利阵营：").append(campLabel(report.winnerCamp())).append(System.lineSeparator())
                .append("- 说明：privateThought 是模型原始文本，不等同于规则允许的确定知识").append(System.lineSeparator());

        if (!report.players().isEmpty()) {
            builder.append(System.lineSeparator())
                    .append("## 角色总表")
                    .append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append(renderMarkdownTable(
                            List.of("座位", "玩家", "角色", "阵营", "初始私有知识"),
                            report.players().stream()
                                    .map(player -> List.of(
                                            Objects.toString(player.seatNo(), "-"),
                                            reportPlayerLabel(playersById, session, player.playerId()),
                                            roleLabel(player.roleId()),
                                            campLabel(player.camp()),
                                            orDash(player.privateKnowledgeSummary())
                                    ))
                                    .toList()
                    ))
                    .append(System.lineSeparator());
        }

        for (ConsoleDecisionSection section : report.sections()) {
            builder.append(System.lineSeparator())
                    .append("## ").append(section.title())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
            String summary = reportSectionSummary(section, session, playersById);
            if (summary != null) {
                builder.append(summary)
                        .append(System.lineSeparator())
                        .append(System.lineSeparator());
            }
            if (section.rows().isEmpty()) {
                builder.append("本节没有可展示的 agent 决策。")
                        .append(System.lineSeparator());
                continue;
            }
            builder.append(renderMarkdownTable(
                            List.of("序号", "阶段", "玩家", "角色", "动作", "公开发言", "私有思考", "备注"),
                            section.rows().stream()
                                    .map(row -> List.of(
                                            Objects.toString(row.eventSeqNo(), "-"),
                                            phaseLabel(row.phase()),
                                            reportPlayerLabel(playersById, session, row.playerId()),
                                            roleLabel(row.roleId()),
                                            reportActionLabel(row, session, playersById),
                                            orDash(row.publicSpeech()),
                                            orDash(row.privateThought()),
                                            orDash(row.note())
                                    ))
                                    .toList()
                    ))
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private void appendJsonBlock(StringBuilder builder, String label, String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        builder.append(System.lineSeparator())
                .append("  ")
                .append(label)
                .append("=")
                .append(formatStructuredText(json));
    }

    private void appendResponseDiagnostics(StringBuilder builder, Map<String, Object> rawModelResponse, String privateThought) {
        String finishReason = stringValue(rawModelResponse.get("finishReason"));
        if (finishReason != null) {
            builder.append(System.lineSeparator())
                    .append("  结束原因=").append(finishReason);
        }
        String contentShape = stringValue(rawModelResponse.get("assistantContentShape"));
        if (contentShape != null) {
            builder.append(System.lineSeparator())
                    .append("  响应形态=").append(contentShapeLabel(contentShape));
        }
        String assistantPreview = stringValue(rawModelResponse.get("assistantContentPreview"));
        if (assistantPreview != null) {
            builder.append(System.lineSeparator())
                    .append("  响应预览=").append(assistantPreview);
        }
        String reasoningPreview = stringValue(rawModelResponse.get("reasoningDetailsPreview"));
        if (reasoningPreview != null && !Objects.equals(reasoningPreview, privateThought)) {
            builder.append(System.lineSeparator())
                    .append("  推理预览=").append(reasoningPreview);
        }
        String transportSummary = transportFailureSummary(rawModelResponse);
        if (transportSummary != null) {
            builder.append(System.lineSeparator())
                    .append("  传输诊断=").append(transportSummary);
        }
    }

    private void appendOptionalSectionWarnings(StringBuilder builder, Map<String, Object> validation) {
        if (validation == null || validation.isEmpty()) {
            return;
        }
        Object rawWarnings = validation.get("optionalSectionWarnings");
        if (!(rawWarnings instanceof Collection<?> warnings) || warnings.isEmpty()) {
            return;
        }
        for (Object item : warnings) {
            if (!(item instanceof Map<?, ?> warning)) {
                continue;
            }
            String field = stringValue(warning.get("field"));
            String reason = stringValue(warning.get("reason"));
            String preview = stringValue(warning.get("contentPreview"));
            if (field == null && reason == null && preview == null) {
                continue;
            }
            builder.append(System.lineSeparator())
                    .append("  附加字段告警=")
                    .append(field == null ? "-" : field);
            if (reason != null) {
                builder.append(" ")
                        .append(optionalSectionReasonLabel(reason));
            }
            if (preview != null) {
                builder.append("，内容预览=").append(preview);
            }
        }
    }

    private String formatPlayerIdCollection(Object value, ConsoleGameSession session) {
        if (!(value instanceof Collection<?> collection)) {
            return "[]";
        }
        List<String> labels = new ArrayList<>();
        for (Object item : collection) {
            labels.add(session.labelForPlayer(stringValue(item)));
        }
        return labels.toString();
    }

    private String formatSeatCollection(Object value, ConsoleGameSession session) {
        if (!(value instanceof Collection<?> collection)) {
            return "[]";
        }
        List<String> labels = new ArrayList<>();
        for (Object item : collection) {
            labels.add(session.labelForSeat(item));
        }
        return labels.toString();
    }

    private String formatSimpleCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::displayValue).collect(Collectors.toList()).toString();
        }
        return "[]";
    }

    private String formatActionCollection(Collection<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return "[]";
        }
        return actions.stream().map(this::actionTypeLabel).collect(Collectors.toList()).toString();
    }

    private String displayValue(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value);
        if ("GOOD".equals(text) || "EVIL".equals(text)) {
            return campLabel(text);
        }
        if (isRoleId(text)) {
            return roleLabel(text);
        }
        if (isActionType(text)) {
            return actionTypeLabel(text);
        }
        return text;
    }

    private boolean isRoleId(String text) {
        return switch (text) {
            case "MERLIN", "PERCIVAL", "LOYAL_SERVANT", "MORGANA", "ASSASSIN" -> true;
            default -> false;
        };
    }

    private boolean isActionType(String text) {
        return switch (text) {
            case "PUBLIC_SPEECH", "TEAM_PROPOSAL", "TEAM_VOTE", "MISSION_ACTION", "ASSASSINATION" -> true;
            default -> false;
        };
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String string = String.valueOf(value);
        return string.isBlank() ? null : string;
    }

    private String formatStructuredText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        try {
            Object parsed = objectMapper.readValue(raw, new TypeReference<Object>() { });
            return formatJson(parsed);
        } catch (JsonProcessingException ignored) {
            return raw;
        }
    }

    private String formatJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String compactJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Object parsed = objectMapper.readValue(json, new TypeReference<Object>() { });
            return objectMapper.writeValueAsString(parsed);
        } catch (JsonProcessingException ignored) {
            return json;
        }
    }

    private String privateThought(GameAuditEntryResponse entry) {
        Map<String, Object> rawModelResponse = structuredMap(entry.getRawModelResponseJson());
        String privateThought = stringValue(rawModelResponse.get("privateThought"));
        if (privateThought != null) {
            return privateThought;
        }
        String reasoningPreview = stringValue(rawModelResponse.get("reasoningDetailsPreview"));
        if (reasoningPreview != null) {
            return reasoningPreview;
        }
        return reasonSummary(entry.getAuditReasonJson());
    }

    private String reportSectionSummary(ConsoleDecisionSection section,
                                        ConsoleGameSession session,
                                        Map<String, ConsoleDecisionPlayer> playersById) {
        List<String> parts = new ArrayList<>();
        if (section.leaderPlayerId() != null) {
            parts.add("队长=" + reportPlayerLabel(playersById, session, section.leaderPlayerId()));
        }
        if (!section.teamPlayerIds().isEmpty()) {
            parts.add("队伍=" + section.teamPlayerIds().stream()
                    .map(playerId -> reportPlayerLabel(playersById, session, playerId))
                    .collect(Collectors.toList()));
        }
        if (!section.votes().isEmpty()) {
            long approves = section.votes().stream()
                    .filter(vote -> "APPROVE".equals(vote.vote()))
                    .count();
            long rejects = section.votes().size() - approves;
            String voteDetail = section.votes().stream()
                    .map(vote -> reportPlayerLabel(playersById, session, vote.playerId()) + voteLabel(vote.vote()))
                    .collect(Collectors.joining("，"));
            String voteSummary = "投票=" + approves + "赞成/" + rejects + "反对";
            if (section.voteRejected()) {
                voteSummary += "，结果=否决";
            } else if (section.missionOutcome() != null) {
                voteSummary += "，结果=通过";
            } else if (section.pauseReason() != null) {
                voteSummary += "，结果=中断";
            }
            if (!voteDetail.isBlank()) {
                voteSummary += "（" + voteDetail + "）";
            }
            parts.add(voteSummary);
        }
        if (section.missionOutcome() != null) {
            if ("FAILED".equals(section.missionOutcome())) {
                parts.add("任务=失败" + (section.missionFailCount() == null ? "" : "（失败票 " + section.missionFailCount() + "）"));
            } else {
                parts.add("任务=成功");
            }
        }
        if (section.pauseReason() != null) {
            parts.add("暂停=" + pauseReasonLabel(section.pauseReason()));
        }
        if (section.winnerCamp() != null) {
            parts.add("胜利=" + campLabel(section.winnerCamp()));
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private String reportActionLabel(ConsoleDecisionRow row,
                                     ConsoleGameSession session,
                                     Map<String, ConsoleDecisionPlayer> playersById) {
        String action = actionTypeLabel(row.actionType());
        if (row.actionDetail() == null) {
            return row.failed() ? action + " [失败]" : action;
        }
        String detail = switch (Objects.toString(row.actionType(), "")) {
            case "TEAM_VOTE" -> voteLabel(row.actionDetail());
            case "MISSION_ACTION" -> missionChoiceLabel(row.actionDetail());
            case "ASSASSINATION" -> "目标=" + reportPlayerLabel(playersById, session, row.actionDetail());
            default -> row.actionDetail();
        };
        return row.failed() ? action + "（" + detail + "，失败）" : action + "（" + detail + "）";
    }

    private Map<String, ConsoleDecisionPlayer> reportPlayersById(ConsoleDecisionReport report) {
        return report.players().stream()
                .filter(player -> player.playerId() != null && !player.playerId().isBlank())
                .collect(Collectors.toMap(
                        ConsoleDecisionPlayer::playerId,
                        player -> player,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private String reportPlayerLabel(Map<String, ConsoleDecisionPlayer> playersById,
                                     ConsoleGameSession session,
                                     String playerId) {
        String sessionLabel = session.labelForPlayer(playerId);
        if (playerId == null || playerId.isBlank() || "SYSTEM".equals(playerId)) {
            return sessionLabel;
        }
        if (!Objects.equals(sessionLabel, playerId)) {
            return sessionLabel;
        }
        ConsoleDecisionPlayer player = playersById.get(playerId);
        if (player == null || player.displayName() == null || player.displayName().isBlank()
                || Objects.equals(player.displayName(), playerId)) {
            return sessionLabel;
        }
        return playerId + "/" + player.displayName();
    }

    private String renderMarkdownTable(List<String> headers, List<List<String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("| ")
                .append(String.join(" | ", headers))
                .append(" |")
                .append(System.lineSeparator())
                .append("| ")
                .append(headers.stream().map(ignored -> "---").collect(Collectors.joining(" | ")))
                .append(" |");
        for (List<String> row : rows) {
            builder.append(System.lineSeparator())
                    .append("| ")
                    .append(row.stream().map(this::escapeMarkdownCell).collect(Collectors.joining(" | ")))
                    .append(" |");
        }
        return builder.toString();
    }

    private String limitConsoleCell(String value, int maxLength) {
        String normalized = orDash(value);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= 1) {
            return normalized.substring(0, maxLength);
        }
        return normalized.substring(0, maxLength - 1) + "…";
    }

    private String escapeMarkdownCell(String value) {
        return orDash(value)
                .replace("|", "\\|")
                .replace("\r", " ")
                .replace("\n", "<br/>");
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String detailedError(GameAuditEntryResponse entry) {
        Map<String, Object> validation = structuredMap(entry.getValidationResultJson());
        String validationError = stringValue(validation.get("errorMessage"));
        if (validationError != null) {
            return validationError;
        }
        return stringValue(entry.getErrorMessage());
    }

    private String transportFailureSummary(Map<String, Object> rawModelResponse) {
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
        String rootClass = stringValue(rawModelResponse.get("rootExceptionClass"));
        if (rootClass != null) {
            parts.add("异常=" + rootClass);
        }
        String rootMessage = stringValue(rawModelResponse.get("rootExceptionMessage"));
        if (rootMessage != null) {
            parts.add("原因=" + rootMessage);
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private String reasonSummary(String auditReasonJson) {
        Map<String, Object> auditReason = structuredMap(auditReasonJson);
        Object summary = auditReason.get("reasonSummary");
        if (summary instanceof Collection<?> collection && !collection.isEmpty()) {
            return collection.stream()
                    .map(String::valueOf)
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining("；"));
        }
        return stringValue(auditReason.get("goal"));
    }

    private String optionalSectionReasonLabel(String reason) {
        if (reason == null) {
            return "-";
        }
        return switch (reason) {
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

    private String selectionModeLabel(String mode) {
        if ("SEAT_BINDING".equalsIgnoreCase(mode)) {
            return "按座位绑定";
        }
        if ("ROLE_BINDING".equalsIgnoreCase(mode)) {
            return "按身份绑定";
        }
        if ("RANDOM_POOL".equalsIgnoreCase(mode)) {
            return "随机模型池";
        }
        return mode;
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case "WAITING" -> "未开始";
            case "RUNNING" -> "进行中";
            case "PAUSED" -> "已暂停";
            case "ENDED" -> "已结束";
            default -> status;
        };
    }

    private String phaseLabel(String phase) {
        if (phase == null) {
            return "-";
        }
        return switch (phase) {
            case "ROLE_REVEAL" -> "身份揭示";
            case "DISCUSSION" -> "公开讨论";
            case "TEAM_PROPOSAL" -> "组队提案";
            case "TEAM_VOTE" -> "队伍投票";
            case "MISSION_ACTION" -> "任务执行";
            case "MISSION_RESOLUTION" -> "任务结算";
            case "ASSASSINATION" -> "刺杀阶段";
            case "GAME_END" -> "游戏结束";
            case "ROUND_START" -> "回合开始";
            case "WAITING_FOR_HUMAN_INPUT" -> "等待人工输入";
            default -> phase;
        };
    }

    private String eventTypeLabel(String eventType) {
        if (eventType == null) {
            return "事件";
        }
        return switch (eventType) {
            case "GAME_CREATED" -> "创建游戏";
            case "GAME_STARTED" -> "开始游戏";
            case "ROLE_ASSIGNED" -> "分配身份";
            case "PLAYER_ACTION" -> "玩家行动";
            case "TEAM_PROPOSED" -> "提出队伍";
            case "TEAM_VOTE_CAST" -> "提交投票";
            case "TEAM_VOTE_REJECTED" -> "队伍被否";
            case "MISSION_ACTION_CAST" -> "任务选择";
            case "MISSION_SUCCESS" -> "任务成功";
            case "MISSION_FAILED" -> "任务失败";
            case "ASSASSINATION_SUBMITTED" -> "提交刺杀";
            case "GAME_PAUSED" -> "游戏暂停";
            case "GAME_ENDED" -> "游戏结束";
            default -> eventType;
        };
    }

    private String replayKindLabel(String replayKind) {
        if (replayKind == null) {
            return "回放";
        }
        return switch (replayKind) {
            case "GAME_STARTUP" -> "游戏启动";
            case "ROUND_OPENING" -> "回合开场";
            case "ROLE_ASSIGNMENT" -> "身份分配";
            case "PLAYER_DECISION" -> "玩家决策";
            case "TEAM_FORMED" -> "队伍成形";
            case "VOTE_RECORDED" -> "投票记录";
            case "VOTE_RESULT" -> "投票结果";
            case "MISSION_DECISION" -> "任务决策";
            case "MISSION_RESULT" -> "任务结果";
            case "ASSASSINATION_RESULT" -> "刺杀结果";
            case "RUN_PAUSED" -> "运行暂停";
            case "GAME_FINAL" -> "终局";
            default -> replayKind;
        };
    }

    private String actionTypeLabel(Object actionType) {
        String value = stringValue(actionType);
        if (value == null) {
            return "-";
        }
        return switch (value) {
            case "PUBLIC_SPEECH" -> "公开发言";
            case "TEAM_PROPOSAL" -> "提出队伍";
            case "TEAM_VOTE" -> "队伍投票";
            case "MISSION_ACTION" -> "执行任务";
            case "ASSASSINATION" -> "刺杀";
            default -> value;
        };
    }

    private String roleLabel(Object role) {
        String value = stringValue(role);
        if (value == null) {
            return "-";
        }
        return switch (value) {
            case "MERLIN" -> "梅林";
            case "PERCIVAL" -> "派西维尔";
            case "LOYAL_SERVANT" -> "忠臣";
            case "MORGANA" -> "莫甘娜";
            case "ASSASSIN" -> "刺客";
            default -> value;
        };
    }

    private String campLabel(Object camp) {
        String value = stringValue(camp);
        if (value == null) {
            return "-";
        }
        return switch (value) {
            case "GOOD" -> "正义方";
            case "EVIL" -> "邪恶方";
            default -> value;
        };
    }

    private String voteLabel(Object vote) {
        String value = stringValue(vote);
        if (value == null) {
            return "-";
        }
        return switch (value) {
            case "APPROVE" -> "赞成";
            case "REJECT" -> "反对";
            default -> value;
        };
    }

    private String missionChoiceLabel(Object choice) {
        String value = stringValue(choice);
        if (value == null) {
            return "-";
        }
        return switch (value) {
            case "SUCCESS" -> "成功";
            case "FAIL" -> "失败";
            default -> value;
        };
    }

    private String pauseReasonLabel(Object reason) {
        String value = stringValue(reason);
        if (value == null) {
            return "-";
        }
        return switch (value) {
            case "LLM_ACTION_FAILURE" -> "大模型动作生成失败";
            default -> value;
        };
    }

    private String probeCheckLabel(String checkType) {
        if (checkType == null) {
            return "检查";
        }
        return switch (checkType) {
            case "CONNECTIVITY" -> "连通性";
            case "STRUCTURED_JSON" -> "结构化 JSON";
            default -> checkType;
        };
    }

    private String diagnosisLabel(String diagnosis) {
        if (diagnosis == null) {
            return "-";
        }
        return switch (diagnosis) {
            case "OK" -> "正常";
            case "NETWORK_OR_AUTH_FAILED" -> "网络或鉴权失败";
            case "NETWORK_OK_BUT_STRUCTURED_JSON_FAILED" -> "网络可达，但结构化 JSON 不兼容";
            case "STRUCTURED_JSON_FAILED" -> "结构化 JSON 不兼容";
            default -> diagnosis;
        };
    }

    private String contentShapeLabel(String contentShape) {
        if (contentShape == null) {
            return "-";
        }
        return switch (contentShape) {
            case "reasoning_only" -> "仅返回 reasoning_details";
            case "missing_content" -> "未返回 content";
            case "json_object" -> "纯 JSON 对象";
            case "think_prefixed_json" -> "带思考前缀的 JSON";
            case "markdown_code_block" -> "Markdown 代码块中的 JSON";
            case "embedded_json_object" -> "说明文字中嵌入 JSON";
            case "truncated_json_candidate" -> "疑似截断 JSON";
            case "markdown_explanation" -> "Markdown 解释文本";
            case "plain_text" -> "纯文本解释";
            default -> contentShape;
        };
    }

    private String booleanLabel(boolean value) {
        return value ? "通过" : "失败";
    }

    private String booleanFlag(boolean value) {
        return value ? "有" : "无";
    }
}
