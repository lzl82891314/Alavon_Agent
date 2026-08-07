package com.example.avalon.runtime.engine;

import com.example.avalon.runtime.model.GameRuntimeState;

public interface GameRuleEngine {
    int missionFailThresholdForRound(GameRuntimeState state);

    boolean shouldEnterAssassination(GameRuntimeState state);

    String resolveWinner(GameRuntimeState state);
}
