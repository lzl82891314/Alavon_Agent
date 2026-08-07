package com.example.avalon.agent.cognition;

public record PlayerBelief(String playerId, String proposition, double probability,
                           String confidence, long evidenceSequence) {
    public PlayerBelief {
        if (probability < 0 || probability > 1) throw new IllegalArgumentException("probability must be between 0 and 1");
    }
}
