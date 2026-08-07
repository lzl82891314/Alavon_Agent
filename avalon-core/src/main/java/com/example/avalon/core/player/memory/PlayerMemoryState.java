package com.example.avalon.core.player.memory;

import com.example.avalon.core.game.enums.Camp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PlayerMemoryState(
        String gameId,
        String playerId,
        Long version,
        String roleId,
        Camp camp,
        Map<String, Double> suspicionScores,
        Map<String, Double> trustScores,
        List<String> observations,
        List<String> commitments,
        List<String> inferredFacts,
        String strategyMode,
        String lastSummary,
        Instant updatedAt
) {
    public PlayerMemoryState {
        suspicionScores = suspicionScores == null ? Map.of() : Map.copyOf(suspicionScores);
        trustScores = trustScores == null ? Map.of() : Map.copyOf(trustScores);
        observations = observations == null ? List.of() : List.copyOf(observations);
        commitments = commitments == null ? List.of() : List.copyOf(commitments);
        inferredFacts = inferredFacts == null ? List.of() : List.copyOf(inferredFacts);
    }

    public static PlayerMemoryState empty(String gameId, String playerId, String roleId, Camp camp, Instant now) {
        return new PlayerMemoryState(
                gameId,
                playerId,
                0L,
                roleId,
                camp,
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                "NEUTRAL",
                null,
                now
        );
    }

    public PlayerMemoryState merge(MemoryUpdate update, Instant now) {
        Map<String, Double> nextSuspicion = new LinkedHashMap<>(suspicionScores);
        update.suspicionDelta().forEach((key, value) -> nextSuspicion.merge(key, value, Double::sum));

        Map<String, Double> nextTrust = new LinkedHashMap<>(trustScores);
        update.trustDelta().forEach((key, value) -> nextTrust.merge(key, value, Double::sum));

        List<String> nextObservations = new ArrayList<>(observations);
        nextObservations.addAll(update.observationsToAdd());

        List<String> nextCommitments = new ArrayList<>(commitments);
        nextCommitments.addAll(update.commitmentsToAdd());

        List<String> nextFacts = new ArrayList<>(inferredFacts);
        nextFacts.addAll(update.inferredFactsToAdd());

        return new PlayerMemoryState(
                gameId,
                playerId,
                version + 1,
                roleId,
                camp,
                nextSuspicion,
                nextTrust,
                nextObservations,
                nextCommitments,
                nextFacts,
                update.strategyMode() == null ? strategyMode : update.strategyMode(),
                update.lastSummary() == null ? lastSummary : update.lastSummary(),
                now
        );
    }
}

