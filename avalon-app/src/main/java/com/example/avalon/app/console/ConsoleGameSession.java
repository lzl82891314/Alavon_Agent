package com.example.avalon.app.console;

import com.example.avalon.agent.model.ModelProfile;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.api.dto.CreateGameRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ConsoleGameSession {
    private String gameId;
    private long lastPrintedEventSeqNo;
    private long lastPrintedAuditEventSeqNo;
    private final Map<String, SeatDescriptor> seatsByPlayerId = new LinkedHashMap<>();
    private final Map<Integer, SeatDescriptor> seatsBySeatNo = new LinkedHashMap<>();
    private Long seed;
    private String llmSelectionSummary;
    private final List<String> llmSelectionDetails = new ArrayList<>();

    String gameId() {
        return gameId;
    }

    boolean hasActiveGame() {
        return gameId != null && !gameId.isBlank();
    }

    long lastPrintedEventSeqNo() {
        return lastPrintedEventSeqNo;
    }

    long lastPrintedAuditEventSeqNo() {
        return lastPrintedAuditEventSeqNo;
    }

    Long seed() {
        return seed;
    }

    void updateLastPrintedEventSeqNo(long seqNo) {
        lastPrintedEventSeqNo = Math.max(lastPrintedEventSeqNo, seqNo);
    }

    void updateLastPrintedAuditEventSeqNo(long seqNo) {
        lastPrintedAuditEventSeqNo = Math.max(lastPrintedAuditEventSeqNo, seqNo);
    }

    void activateNewGame(String gameId, CreateGameRequest request) {
        this.gameId = gameId;
        this.lastPrintedEventSeqNo = 0L;
        this.lastPrintedAuditEventSeqNo = 0L;
        this.seatsByPlayerId.clear();
        this.seatsBySeatNo.clear();
        this.seed = request.getSeed();
        this.llmSelectionSummary = null;
        this.llmSelectionDetails.clear();

        boolean pooledSelectionEnabled = request.getLlmSelection() != null
                && request.getLlmSelection().getMode() != null
                && !request.getLlmSelection().getMode().isBlank();

        int playerIndex = 1;
        for (CreateGameRequest.PlayerSlotRequest player : request.getPlayers()) {
            int seatNo = player.getSeatNo() == null ? playerIndex : player.getSeatNo();
            String playerId = "P" + playerIndex;
            String displayName = blankToDefault(player.getDisplayName(), playerId);
            SeatDescriptor descriptor = new SeatDescriptor(
                    playerId,
                    seatNo,
                    displayName,
                    controllerLabel(player.getControllerType(), player.getAgentConfig(), pooledSelectionEnabled),
                    provider(player.getAgentConfig()),
                    modelName(player.getAgentConfig())
            );
            seatsByPlayerId.put(playerId, descriptor);
            seatsBySeatNo.put(seatNo, descriptor);
            playerIndex++;
        }

        if (pooledSelectionEnabled) {
            llmSelectionSummary = request.getLlmSelection().getMode();
            if ("SEAT_BINDING".equalsIgnoreCase(request.getLlmSelection().getMode())) {
                request.getLlmSelection().getSeatBindings().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                        .forEach(entry -> llmSelectionDetails.add(labelForSeat(entry.getKey()) + " -> " + entry.getValue()));
            } else if ("ROLE_BINDING".equalsIgnoreCase(request.getLlmSelection().getMode())) {
                request.getLlmSelection().getRoleBindings().forEach((roleId, modelId) ->
                        llmSelectionDetails.add(roleId + " -> " + modelId));
            } else if ("RANDOM_POOL".equalsIgnoreCase(request.getLlmSelection().getMode())) {
                llmSelectionDetails.add("pool=" + request.getLlmSelection().getCandidateModelIds());
            }
        }
    }

    void useExistingGame(String gameId) {
        this.gameId = gameId;
        this.lastPrintedEventSeqNo = 0L;
        this.lastPrintedAuditEventSeqNo = 0L;
        this.seatsByPlayerId.clear();
        this.seatsBySeatNo.clear();
        this.seed = null;
        this.llmSelectionSummary = null;
        this.llmSelectionDetails.clear();
    }

    Collection<SeatDescriptor> seats() {
        return List.copyOf(seatsByPlayerId.values());
    }

    String llmSelectionSummary() {
        return llmSelectionSummary;
    }

    Collection<String> llmSelectionDetails() {
        return List.copyOf(llmSelectionDetails);
    }

    String labelForPlayer(String playerId) {
        if (playerId == null || playerId.isBlank() || "SYSTEM".equals(playerId)) {
            return "系统";
        }
        SeatDescriptor descriptor = seatsByPlayerId.get(playerId);
        if (descriptor == null || descriptor.displayName().equals(playerId)) {
            return playerId;
        }
        return playerId + "/" + descriptor.displayName();
    }

    String labelForSeat(Object seatNoValue) {
        Integer seatNo = parseSeatNo(seatNoValue);
        if (seatNo == null) {
            return String.valueOf(seatNoValue);
        }
        SeatDescriptor descriptor = seatsBySeatNo.get(seatNo);
        if (descriptor == null) {
            return seatNo + "号位";
        }
        return seatNo + "号位(" + labelForPlayer(descriptor.playerId()) + ")";
    }

    private Integer parseSeatNo(Object seatNoValue) {
        if (seatNoValue instanceof Integer integer) {
            return integer;
        }
        if (seatNoValue instanceof Number number) {
            return number.intValue();
        }
        if (seatNoValue instanceof String string && !string.isBlank()) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String controllerLabel(String controllerType, PlayerAgentConfig agentConfig, boolean pooledSelectionEnabled) {
        String normalized = blankToDefault(controllerType, "SCRIPTED").trim().toUpperCase();
        if (!"LLM".equals(normalized)) {
            return "SCRIPTED".equals(normalized) ? "脚本控制" : normalized;
        }
        String provider = provider(agentConfig);
        if (provider == null) {
            return pooledSelectionEnabled ? "大模型(模型池)" : "大模型(noop回退)";
        }
        return "大模型(" + provider + ")";
    }

    private String provider(PlayerAgentConfig agentConfig) {
        if (agentConfig == null) {
            return null;
        }
        ModelProfile modelProfile = agentConfig.getModelProfile();
        if (modelProfile == null || modelProfile.getProvider() == null || modelProfile.getProvider().isBlank()) {
            return null;
        }
        return modelProfile.getProvider();
    }

    private String modelName(PlayerAgentConfig agentConfig) {
        if (agentConfig == null || agentConfig.getModelProfile() == null) {
            return null;
        }
        String modelName = agentConfig.getModelProfile().getModelName();
        return modelName == null || modelName.isBlank() ? null : modelName;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    record SeatDescriptor(
            String playerId,
            int seatNo,
            String displayName,
            String controllerType,
            String provider,
            String modelName
    ) {
        String summary() {
            List<String> parts = new ArrayList<>();
            parts.add(seatNo + "号位");
            parts.add(playerId.equals(displayName) ? playerId : playerId + "/" + displayName);
            parts.add(controllerType);
            if (modelName != null) {
                parts.add("模型=" + modelName);
            }
            return String.join(" | ", parts);
        }
    }
}
