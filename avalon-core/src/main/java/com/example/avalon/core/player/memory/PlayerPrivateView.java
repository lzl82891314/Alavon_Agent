package com.example.avalon.core.player.memory;

import com.example.avalon.core.game.enums.Camp;

import java.util.List;

public record PlayerPrivateView(
        String gameId,
        String playerId,
        Integer seatNo,
        String roleId,
        Camp camp,
        PlayerPrivateKnowledge knowledge,
        List<String> revealedCapabilities
) {
    public PlayerPrivateView {
        revealedCapabilities = revealedCapabilities == null ? List.of() : List.copyOf(revealedCapabilities);
    }
}

