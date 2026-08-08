package com.example.avalon.runtime.service;

import com.example.avalon.core.game.observation.FactScope;
import com.example.avalon.core.game.observation.ObservedGameEvent;
import com.example.avalon.core.game.observation.PlayerObservationBatch;
import com.example.avalon.core.player.memory.PlayerMemoryState;
import com.example.avalon.runtime.model.GameEvent;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.disclosure.GameEventVisibilityPolicy;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Builds the only event stream an agent is allowed to observe. */
public final class PlayerObservationProjector {
    public PlayerObservationBatch project(GameRuntimeState state, String playerId, PlayerMemoryState memory) {
        long cursor = memory.lastObservedSequence() == null ? 0L : memory.lastObservedSequence();
        long latest = state.events().isEmpty() ? 0L : state.events().get(state.events().size() - 1).seqNo();
        List<ObservedGameEvent> events = state.events().stream()
                .filter(event -> event.seqNo() > cursor)
                .filter(event -> GameEventVisibilityPolicy.isPublic(event.type()))
                .map(this::project)
                .toList();
        return new PlayerObservationBatch(
                state.generatedGameId(),
                playerId,
                memory.agentInstanceId(),
                cursor,
                latest,
                events);
    }

    private ObservedGameEvent project(GameEvent event) {
        String utterance = text(event.payload().get("speech"));
        FactScope scope = "PUBLIC_SPEECH".equals(event.payload().get("actionType")) && utterance != null
                ? FactScope.PUBLIC_CLAIM
                : FactScope.WORLD_FACT;
        return new ObservedGameEvent(
                event.seqNo(),
                event.type(),
                event.actorId(),
                scope,
                event.payload(),
                utterance,
                text(event.payload().get("speechAct")),
                strings(event.payload().get("mentions")),
                longs(event.payload().get("replyToEventSequences")),
                event.createdAt());
    }

    private String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    private List<Long> longs(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        return values.stream().filter(Number.class::isInstance).map(Number.class::cast).map(Number::longValue).toList();
    }
}
