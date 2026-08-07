package com.example.avalon.agent.gateway;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.agent.model.AuditReason;
import com.example.avalon.agent.model.MemoryUpdate;
import com.example.avalon.agent.model.RawCompletionMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NoopAgentGateway implements AgentGateway {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public AgentTurnResult playTurn(AgentTurnRequest request) {
        AgentTurnResult result = new AgentTurnResult();
        result.setPublicSpeech(buildSpeech(request));
        result.setPrivateThought(buildPrivateThought(request));
        result.setActionJson(writeJson(actionPayload(request, result.getPublicSpeech())));
        result.setAuditReason(buildAuditReason(request));
        result.setMemoryUpdate(buildMemoryUpdate(request));
        result.setModelMetadata(buildMetadata(request));
        return result;
    }

    private Map<String, Object> actionPayload(AgentTurnRequest request, String speech) {
        List<String> allowedActions = request.getAllowedActions();
        if (allowedActions.isEmpty()) {
            throw new IllegalStateException("No allowed actions for request " + request.getPlayerId());
        }

        String actionType = allowedActions.get(0);
        return switch (actionType) {
            case "PUBLIC_SPEECH" -> Map.of(
                    "actionType", actionType,
                    "speechText", speech
            );
            case "TEAM_PROPOSAL" -> Map.of(
                    "actionType", actionType,
                    "selectedPlayerIds", buildProposal(request)
            );
            case "TEAM_VOTE" -> Map.of(
                    "actionType", actionType,
                    "vote", "APPROVE"
            );
            case "MISSION_ACTION" -> Map.of(
                    "actionType", actionType,
                    "choice", "EVIL".equals(String.valueOf(request.getPrivateKnowledge().get("camp"))) ? "FAIL" : "SUCCESS"
            );
            case "ASSASSINATION" -> Map.of(
                    "actionType", actionType,
                    "targetPlayerId", chooseAssassinationTarget(request)
            );
            default -> throw new IllegalStateException("Unsupported noop action type: " + actionType);
        };
    }

    private String buildSpeech(AgentTurnRequest request) {
        return "%s号位表示：第%s轮%s阶段按既定策略行动。".formatted(
                request.getSeatNo(),
                request.getRoundNo(),
                phaseLabel(request.getPhase())
        );
    }

    private String buildPrivateThought(AgentTurnRequest request) {
        return "我是%s号位，当前阶段是%s，可执行动作是%s。我会优先给出一个稳定且合法的默认动作。".formatted(
                request.getSeatNo(),
                phaseLabel(request.getPhase()),
                request.getAllowedActions().stream().map(this::actionLabel).toList()
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> buildProposal(AgentTurnRequest request) {
        int teamSize = ((Number) request.getPublicState().getOrDefault("teamSize", 2)).intValue();
        List<Map<String, Object>> players = (List<Map<String, Object>>) request.getPublicState().getOrDefault("players", List.of());
        List<String> proposal = new ArrayList<>();
        proposal.add(request.getPlayerId());

        int currentSeat = request.getSeatNo();
        while (proposal.size() < teamSize) {
            currentSeat = seatAtOffset(players.size(), currentSeat, 1);
            String playerId = playerIdForSeat(players, currentSeat);
            if (playerId != null && !proposal.contains(playerId)) {
                proposal.add(playerId);
            }
        }
        return proposal;
    }

    private int seatAtOffset(int playerCount, int seatNo, int offset) {
        int index = (seatNo - 1 + offset) % playerCount;
        if (index < 0) {
            index += playerCount;
        }
        return index + 1;
    }

    private String playerIdForSeat(List<Map<String, Object>> players, int seatNo) {
        for (Map<String, Object> player : players) {
            if (((Number) player.getOrDefault("seatNo", 0)).intValue() == seatNo) {
                return String.valueOf(player.get("playerId"));
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String chooseAssassinationTarget(AgentTurnRequest request) {
        List<Map<String, Object>> visiblePlayers = (List<Map<String, Object>>) request.getPrivateKnowledge().getOrDefault("visiblePlayers", List.of());
        for (Map<String, Object> visiblePlayer : visiblePlayers) {
            if ("MERLIN".equals(String.valueOf(visiblePlayer.get("exactRoleId")))) {
                return String.valueOf(visiblePlayer.get("playerId"));
            }
        }
        for (Map<String, Object> visiblePlayer : visiblePlayers) {
            List<String> candidateRoleIds = (List<String>) visiblePlayer.getOrDefault("candidateRoleIds", List.of());
            if (candidateRoleIds.contains("MERLIN")) {
                return String.valueOf(visiblePlayer.get("playerId"));
            }
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) request.getPublicState().getOrDefault("players", List.of());
        for (Map<String, Object> player : players) {
            String playerId = String.valueOf(player.get("playerId"));
            if (!request.getPlayerId().equals(playerId)) {
                return playerId;
            }
        }
        return request.getPlayerId();
    }

    private AuditReason buildAuditReason(AgentTurnRequest request) {
        AuditReason reason = new AuditReason();
        reason.setGoal("生成一个合法的" + actionLabel(request.getAllowedActions().get(0)) + "动作");
        reason.setReasonSummary(List.of(
                "遵循当前阶段允许的动作约束",
                "使用确定性的 noop 回退策略"
        ));
        reason.setConfidence(0.25);
        reason.setBeliefs(Map.of("mode", "noop-gateway", "language", "zh-CN"));
        return reason;
    }

    private MemoryUpdate buildMemoryUpdate(AgentTurnRequest request) {
        MemoryUpdate update = new MemoryUpdate();
        update.setObservationsToAdd(List.of("观察到阶段 " + phaseLabel(request.getPhase())));
        update.setStrategyMode("SAFE_DEFAULT");
        update.setLastSummary("noop 网关生成了确定性动作");
        return update;
    }

    private RawCompletionMetadata buildMetadata(AgentTurnRequest request) {
        RawCompletionMetadata metadata = new RawCompletionMetadata();
        metadata.setProvider("noop");
        metadata.setModelName("deterministic-fallback");
        metadata.setInputTokens((long) request.getPromptText().length());
        metadata.setOutputTokens(32L);
        metadata.setAttributes(Map.of("schemaVersion", request.getOutputSchemaVersion()));
        return metadata;
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize deterministic noop action", e);
        }
    }

    private String phaseLabel(String phase) {
        if (phase == null) {
            return "未知";
        }
        return switch (phase) {
            case "DISCUSSION" -> "公开讨论";
            case "TEAM_PROPOSAL" -> "组队提案";
            case "TEAM_VOTE" -> "队伍投票";
            case "MISSION_ACTION" -> "任务执行";
            case "MISSION_RESOLUTION" -> "任务结算";
            case "ASSASSINATION" -> "刺杀";
            default -> phase;
        };
    }

    private String actionLabel(String actionType) {
        if (actionType == null) {
            return "动作";
        }
        return switch (actionType) {
            case "PUBLIC_SPEECH" -> "公开发言";
            case "TEAM_PROPOSAL" -> "组队提案";
            case "TEAM_VOTE" -> "队伍投票";
            case "MISSION_ACTION" -> "任务执行";
            case "ASSASSINATION" -> "刺杀";
            default -> actionType;
        };
    }
}
