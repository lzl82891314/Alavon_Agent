package com.example.avalon.core.player.memory;

import java.util.List;

public record PlayerPrivateKnowledge(
        List<VisiblePlayerInfo> visiblePlayers,
        List<String> notes
) {
    public PlayerPrivateKnowledge {
        visiblePlayers = visiblePlayers == null ? List.of() : List.copyOf(visiblePlayers);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}

