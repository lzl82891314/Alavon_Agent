package com.example.avalon.runtime.orchestrator;

import com.example.avalon.runtime.model.GameEvent;
import com.example.avalon.runtime.model.GameRuntimeState;

import java.util.List;

public record GameRunResult(GameRuntimeState state, List<GameEvent> events, List<String> transcript) {
}

