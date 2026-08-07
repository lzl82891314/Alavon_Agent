package com.example.avalon.runtime.model;

import com.example.avalon.core.player.enums.PlayerControllerType;

import java.util.LinkedHashMap;
import java.util.Map;

public record PlayerRegistration(
        String playerId,
        int seatNo,
        String displayName,
        PlayerControllerType controllerType,
        Map<String, Object> controllerConfig) {
    public PlayerRegistration {
        controllerConfig = controllerConfig == null ? Map.of() : new LinkedHashMap<>(controllerConfig);
    }

    public PlayerRegistration(String playerId, int seatNo, String displayName, PlayerControllerType controllerType) {
        this(playerId, seatNo, displayName, controllerType, Map.of());
    }
}
