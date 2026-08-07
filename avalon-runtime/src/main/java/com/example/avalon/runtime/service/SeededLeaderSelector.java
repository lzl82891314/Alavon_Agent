package com.example.avalon.runtime.service;

import com.example.avalon.runtime.model.PlayerRegistration;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class SeededLeaderSelector {
    private SeededLeaderSelector() {
    }

    public static int initialLeaderSeat(List<PlayerRegistration> players, long seed) {
        if (players == null || players.isEmpty()) {
            return 0;
        }
        List<PlayerRegistration> orderedPlayers = players.stream()
                .sorted(Comparator.comparingInt(PlayerRegistration::seatNo))
                .toList();
        return orderedPlayers.get(new Random(seed).nextInt(orderedPlayers.size())).seatNo();
    }
}
