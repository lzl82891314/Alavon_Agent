package com.example.avalon.agent.cognition;

import java.util.Map;

public record StrategyState(String objective, Map<String, Double> riskByPlayer, long basedOnSequence) {
    public StrategyState { riskByPlayer = riskByPlayer == null ? Map.of() : Map.copyOf(riskByPlayer); }
}
