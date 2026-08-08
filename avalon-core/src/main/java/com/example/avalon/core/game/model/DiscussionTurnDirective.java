package com.example.avalon.core.game.model;

import java.util.List;

public record DiscussionTurnDirective(
        String stage,
        List<String> allowedSpeechActs,
        String responseTargetPlayerId,
        Long replyToEventSequence,
        int remainingPublicTurns
) {
    public DiscussionTurnDirective {
        allowedSpeechActs = allowedSpeechActs == null ? List.of() : List.copyOf(allowedSpeechActs);
    }

    public static DiscussionTurnDirective none() {
        return new DiscussionTurnDirective("NONE", List.of(), null, null, 0);
    }
}
