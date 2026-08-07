package com.example.avalon.runtime.service;

import com.example.avalon.runtime.model.GameRuntimeState;

import java.util.Map;

@FunctionalInterface
public interface ResolvedLlmConfigInitializer {
    ResolvedLlmConfigInitializer NOOP = ignored -> Map.of();

    Map<String, Map<String, Object>> resolve(GameRuntimeState state);
}
