package com.example.avalon.core.game.model;

import com.example.avalon.core.game.enums.PlayerActionType;
import java.util.List;

public record PublicSpeechAction(String speechText, String speechAct, List<String> mentions,
                                 List<Long> replyToEventSequences, Long supersedesSequence) implements PlayerAction {
    public PublicSpeechAction {
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
        replyToEventSequences = replyToEventSequences == null ? List.of() : List.copyOf(replyToEventSequences);
    }

    public PublicSpeechAction(String speechText, String speechAct, List<String> mentions,
                              List<Long> replyToEventSequences) {
        this(speechText, speechAct, mentions, replyToEventSequences, null);
    }

    public PublicSpeechAction(String speechText) {
        this(speechText, "STATE_OPINION", List.of(), List.of(), null);
    }

    @Override
    public PlayerActionType actionType() {
        return PlayerActionType.PUBLIC_SPEECH;
    }
}

