package com.example.avalon.runtime.controller;

import com.example.avalon.core.player.controller.PlayerController;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.PlayerRegistration;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public class PlayerControllerResolver {
    private final Map<PlayerControllerType, BiFunction<GameRuntimeState, PlayerRegistration, PlayerController>> controllers = new EnumMap<>(PlayerControllerType.class);
    private final PlayerController scriptedController = new ScriptedPlayerController();

    public PlayerControllerResolver() {
        register(PlayerControllerType.SCRIPTED, scriptedController);
    }

    public PlayerControllerResolver register(PlayerControllerType type, PlayerController controller) {
        return registerFactory(type, (ignoredState, ignoredPlayer) -> Objects.requireNonNull(controller, "controller"));
    }

    public PlayerControllerResolver registerFactory(PlayerControllerType type,
                                                    BiFunction<GameRuntimeState, PlayerRegistration, PlayerController> controllerFactory) {
        controllers.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(controllerFactory, "controllerFactory"));
        return this;
    }

    public PlayerController resolve(GameRuntimeState state, PlayerRegistration player) {
        BiFunction<GameRuntimeState, PlayerRegistration, PlayerController> controllerFactory = controllers.get(player.controllerType());
        if (controllerFactory == null) {
            throw new IllegalStateException("No controller registered for " + player.controllerType());
        }
        return controllerFactory.apply(state, player);
    }
}
