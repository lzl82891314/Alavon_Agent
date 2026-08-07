package com.example.avalon.agent.cognition;

import java.util.List;

public record CommunicationPlan(List<String> talkingPoints, String speechAct, long basedOnSequence) {
    public CommunicationPlan { talkingPoints = talkingPoints == null ? List.of() : List.copyOf(talkingPoints); }
}
