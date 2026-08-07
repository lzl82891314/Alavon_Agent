package com.example.avalon.runtime.service;

import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameSetup;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GameSessionService {
    private final Map<String, GameRuntimeState> sessions = new ConcurrentHashMap<>();

    public GameRuntimeState create(GameSetup setup) {
        GameRuntimeState state = new GameRuntimeState(setup);
        sessions.put(state.generatedGameId(), state);
        return state;
    }

    public Optional<GameRuntimeState> find(String gameId) {
        return Optional.ofNullable(sessions.get(gameId));
    }

    public GameRuntimeState require(String gameId) {
        return find(gameId).orElseThrow(() -> new IllegalArgumentException("Unknown game: " + gameId));
    }

    public GameRuntimeState save(GameRuntimeState state) {
        sessions.put(state.generatedGameId(), state);
        return state;
    }

    public void evict(String gameId) {
        sessions.remove(gameId);
    }

    public void clear() {
        sessions.clear();
    }
}
