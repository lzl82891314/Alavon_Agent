package com.example.avalon.agent.cognition;

import java.util.List;

public record BeliefState(List<PlayerBelief> playerBeliefs, long basedOnSequence) {
    public BeliefState { playerBeliefs = playerBeliefs == null ? List.of() : List.copyOf(playerBeliefs); }
}
