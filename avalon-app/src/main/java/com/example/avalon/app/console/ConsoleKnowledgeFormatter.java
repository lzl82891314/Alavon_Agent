package com.example.avalon.app.console;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.player.memory.VisiblePlayerInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ConsoleKnowledgeFormatter {
    private ConsoleKnowledgeFormatter() {
    }

    static String summarize(Map<String, Object> privateKnowledge) {
        Map<String, Object> knowledge = privateKnowledge == null ? Map.of() : new LinkedHashMap<>(privateKnowledge);
        List<String> parts = new ArrayList<>();
        Object rawVisiblePlayers = knowledge.get("visiblePlayers");
        if (rawVisiblePlayers instanceof Collection<?> visiblePlayers) {
            for (Object visiblePlayer : visiblePlayers) {
                String summary = summarizeVisiblePlayer(visiblePlayer);
                if (summary != null) {
                    parts.add(summary);
                }
            }
        }
        List<String> notes = stringList(knowledge.get("notes"));
        if (!notes.isEmpty()) {
            parts.add("notes: " + String.join(" | ", notes));
        }
        return parts.isEmpty() ? "无" : String.join("；", parts);
    }

    static String summarizeVisiblePlayer(Object rawVisiblePlayer) {
        if (rawVisiblePlayer instanceof VisiblePlayerInfo visiblePlayer) {
            return summarizeVisiblePlayer(
                    visiblePlayer.playerId(),
                    visiblePlayer.displayName(),
                    visiblePlayer.exactRoleId(),
                    visiblePlayer.camp(),
                    visiblePlayer.candidateRoleIds()
            );
        }
        if (!(rawVisiblePlayer instanceof Map<?, ?> visiblePlayer)) {
            return null;
        }
        return summarizeVisiblePlayer(
                stringValue(visiblePlayer.get("playerId")),
                stringValue(visiblePlayer.get("displayName")),
                stringValue(visiblePlayer.get("exactRoleId")),
                visiblePlayer.get("camp"),
                stringList(visiblePlayer.get("candidateRoleIds"))
        );
    }

    private static String summarizeVisiblePlayer(String playerId,
                                                String displayName,
                                                String exactRoleId,
                                                Object camp,
                                                List<String> candidateRoleIds) {
        String playerLabel = playerLabel(playerId, displayName);
        if (playerLabel == null) {
            return null;
        }
        if (exactRoleId != null) {
            return playerLabel + "=" + roleLabel(exactRoleId);
        }
        if (candidateRoleIds != null && !candidateRoleIds.isEmpty()) {
            return playerLabel + "∈[" + String.join(", ", candidateRoleIds.stream()
                    .map(ConsoleKnowledgeFormatter::roleLabel)
                    .toList()) + "]";
        }
        String campLabel = campLabel(camp);
        if (campLabel != null) {
            return playerLabel + "=" + campLabel;
        }
        return playerLabel;
    }

    private static String playerLabel(String playerId, String displayName) {
        if (playerId == null) {
            return null;
        }
        if (displayName == null || Objects.equals(displayName, playerId)) {
            return playerId;
        }
        return playerId + "/" + displayName;
    }

    private static String roleLabel(String roleId) {
        return switch (Objects.toString(roleId, "")) {
            case "MERLIN" -> "梅林";
            case "PERCIVAL" -> "派西维尔";
            case "LOYAL_SERVANT" -> "忠臣";
            case "MORGANA" -> "莫甘娜";
            case "ASSASSIN" -> "刺客";
            default -> roleId;
        };
    }

    private static String campLabel(Object rawCamp) {
        String camp = rawCamp instanceof Camp value ? value.name() : stringValue(rawCamp);
        return switch (Objects.toString(camp, "")) {
            case "GOOD" -> "好人阵营";
            case "EVIL" -> "邪恶阵营";
            default -> null;
        };
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .map(ConsoleKnowledgeFormatter::stringValue)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
