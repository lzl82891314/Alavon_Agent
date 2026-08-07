package com.example.avalon.core.game.model;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.GameStatus;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.VoteChoice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GameSession {
    private final String gameId;
    private final String ruleSetId;
    private final String ruleSetVersion;
    private final String setupTemplateId;
    private final GameStatus status;
    private final GamePhase phase;
    private final Integer roundNo;
    private final Integer failedTeamVoteCount;
    private final Long randomSeed;
    private final Integer currentLeaderSeat;
    private final Integer successfulMissionCount;
    private final Integer failedMissionCount;
    private final List<String> currentTeamPlayerIds;
    private final Map<String, VoteChoice> currentTeamVotes;
    private final Map<String, MissionChoice> currentMissionChoices;
    private final Camp winnerCamp;
    private final String winnerReason;
    private final String currentAssassinationTargetPlayerId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private GameSession(Builder builder) {
        this.gameId = builder.gameId;
        this.ruleSetId = builder.ruleSetId;
        this.ruleSetVersion = builder.ruleSetVersion;
        this.setupTemplateId = builder.setupTemplateId;
        this.status = builder.status;
        this.phase = builder.phase;
        this.roundNo = builder.roundNo;
        this.failedTeamVoteCount = builder.failedTeamVoteCount;
        this.randomSeed = builder.randomSeed;
        this.currentLeaderSeat = builder.currentLeaderSeat;
        this.successfulMissionCount = builder.successfulMissionCount;
        this.failedMissionCount = builder.failedMissionCount;
        this.currentTeamPlayerIds = builder.currentTeamPlayerIds == null ? List.of() : List.copyOf(builder.currentTeamPlayerIds);
        this.currentTeamVotes = builder.currentTeamVotes == null ? Map.of() : Map.copyOf(builder.currentTeamVotes);
        this.currentMissionChoices = builder.currentMissionChoices == null ? Map.of() : Map.copyOf(builder.currentMissionChoices);
        this.winnerCamp = builder.winnerCamp;
        this.winnerReason = builder.winnerReason;
        this.currentAssassinationTargetPlayerId = builder.currentAssassinationTargetPlayerId;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static GameSession createWaiting(
            String gameId,
            String ruleSetId,
            String ruleSetVersion,
            String setupTemplateId,
            Long randomSeed,
            Instant createdAt,
            Instant updatedAt
    ) {
        return builder()
                .gameId(gameId)
                .ruleSetId(ruleSetId)
                .ruleSetVersion(ruleSetVersion)
                .setupTemplateId(setupTemplateId)
                .status(GameStatus.WAITING)
                .phase(GamePhase.ROLE_REVEAL)
                .roundNo(0)
                .failedTeamVoteCount(0)
                .randomSeed(randomSeed)
                .currentLeaderSeat(1)
                .successfulMissionCount(0)
                .failedMissionCount(0)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public GameSession startRunning(Instant updatedAt) {
        return toBuilder()
                .status(GameStatus.RUNNING)
                .phase(GamePhase.DISCUSSION)
                .roundNo(1)
                .failedTeamVoteCount(0)
                .successfulMissionCount(0)
                .failedMissionCount(0)
                .currentTeamPlayerIds(List.of())
                .currentTeamVotes(Map.of())
                .currentMissionChoices(Map.of())
                .winnerCamp(null)
                .winnerReason(null)
                .currentAssassinationTargetPlayerId(null)
                .updatedAt(updatedAt)
                .build();
    }

    public GameSession withPhase(GamePhase nextPhase, Instant updatedAt) {
        return toBuilder().phase(nextPhase).updatedAt(updatedAt).build();
    }

    public GameSession withStatus(GameStatus nextStatus, Instant updatedAt) {
        return toBuilder().status(nextStatus).updatedAt(updatedAt).build();
    }

    public GameSession withLeaderSeat(Integer nextLeaderSeat, Instant updatedAt) {
        return toBuilder().currentLeaderSeat(nextLeaderSeat).updatedAt(updatedAt).build();
    }

    public GameSession withRoundNo(Integer nextRoundNo, Instant updatedAt) {
        return toBuilder().roundNo(nextRoundNo).updatedAt(updatedAt).build();
    }

    public GameSession withFailedTeamVoteCount(Integer nextFailedTeamVoteCount, Instant updatedAt) {
        return toBuilder().failedTeamVoteCount(nextFailedTeamVoteCount).updatedAt(updatedAt).build();
    }

    public GameSession withMissionScore(Integer nextSuccessfulMissionCount, Integer nextFailedMissionCount, Instant updatedAt) {
        return toBuilder()
                .successfulMissionCount(nextSuccessfulMissionCount)
                .failedMissionCount(nextFailedMissionCount)
                .updatedAt(updatedAt)
                .build();
    }

    public GameSession withCurrentTeam(List<String> currentTeamPlayerIds, Instant updatedAt) {
        return toBuilder().currentTeamPlayerIds(currentTeamPlayerIds).updatedAt(updatedAt).build();
    }

    public GameSession withWinner(Camp winnerCamp, String winnerReason, Instant updatedAt) {
        return toBuilder()
                .status(GameStatus.ENDED)
                .phase(GamePhase.GAME_END)
                .winnerCamp(winnerCamp)
                .winnerReason(winnerReason)
                .updatedAt(updatedAt)
                .build();
    }

    public GameSession withAssassinationTarget(String targetPlayerId, Instant updatedAt) {
        return toBuilder().currentAssassinationTargetPlayerId(targetPlayerId).updatedAt(updatedAt).build();
    }

    public GameSession clearCurrentResolutionState(Instant updatedAt) {
        return toBuilder()
                .currentTeamPlayerIds(List.of())
                .currentTeamVotes(Map.of())
                .currentMissionChoices(Map.of())
                .currentAssassinationTargetPlayerId(null)
                .updatedAt(updatedAt)
                .build();
    }

    public GameSession recordTeamVote(String playerId, VoteChoice vote, Instant updatedAt) {
        Map<String, VoteChoice> next = new LinkedHashMap<>(currentTeamVotes);
        next.put(playerId, vote);
        return toBuilder().currentTeamVotes(next).updatedAt(updatedAt).build();
    }

    public GameSession recordMissionChoice(String playerId, MissionChoice choice, Instant updatedAt) {
        Map<String, MissionChoice> next = new LinkedHashMap<>(currentMissionChoices);
        next.put(playerId, choice);
        return toBuilder().currentMissionChoices(next).updatedAt(updatedAt).build();
    }

    public String gameId() {
        return gameId;
    }

    public String ruleSetId() {
        return ruleSetId;
    }

    public String ruleSetVersion() {
        return ruleSetVersion;
    }

    public String setupTemplateId() {
        return setupTemplateId;
    }

    public GameStatus status() {
        return status;
    }

    public GamePhase phase() {
        return phase;
    }

    public Integer roundNo() {
        return roundNo;
    }

    public Integer failedTeamVoteCount() {
        return failedTeamVoteCount;
    }

    public Long randomSeed() {
        return randomSeed;
    }

    public Integer currentLeaderSeat() {
        return currentLeaderSeat;
    }

    public Integer successfulMissionCount() {
        return successfulMissionCount;
    }

    public Integer failedMissionCount() {
        return failedMissionCount;
    }

    public List<String> currentTeamPlayerIds() {
        return currentTeamPlayerIds;
    }

    public Map<String, VoteChoice> currentTeamVotes() {
        return currentTeamVotes;
    }

    public Map<String, MissionChoice> currentMissionChoices() {
        return currentMissionChoices;
    }

    public Camp winnerCamp() {
        return winnerCamp;
    }

    public String winnerReason() {
        return winnerReason;
    }

    public String currentAssassinationTargetPlayerId() {
        return currentAssassinationTargetPlayerId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean hasWinner() {
        return winnerCamp != null;
    }

    public static final class Builder {
        private String gameId;
        private String ruleSetId;
        private String ruleSetVersion;
        private String setupTemplateId;
        private GameStatus status = GameStatus.WAITING;
        private GamePhase phase = GamePhase.ROLE_REVEAL;
        private Integer roundNo = 0;
        private Integer failedTeamVoteCount = 0;
        private Long randomSeed;
        private Integer currentLeaderSeat = 1;
        private Integer successfulMissionCount = 0;
        private Integer failedMissionCount = 0;
        private List<String> currentTeamPlayerIds = List.of();
        private Map<String, VoteChoice> currentTeamVotes = Map.of();
        private Map<String, MissionChoice> currentMissionChoices = Map.of();
        private Camp winnerCamp;
        private String winnerReason;
        private String currentAssassinationTargetPlayerId;
        private Instant createdAt;
        private Instant updatedAt;

        private Builder() {
        }

        private Builder(GameSession session) {
            this.gameId = session.gameId;
            this.ruleSetId = session.ruleSetId;
            this.ruleSetVersion = session.ruleSetVersion;
            this.setupTemplateId = session.setupTemplateId;
            this.status = session.status;
            this.phase = session.phase;
            this.roundNo = session.roundNo;
            this.failedTeamVoteCount = session.failedTeamVoteCount;
            this.randomSeed = session.randomSeed;
            this.currentLeaderSeat = session.currentLeaderSeat;
            this.successfulMissionCount = session.successfulMissionCount;
            this.failedMissionCount = session.failedMissionCount;
            this.currentTeamPlayerIds = session.currentTeamPlayerIds;
            this.currentTeamVotes = session.currentTeamVotes;
            this.currentMissionChoices = session.currentMissionChoices;
            this.winnerCamp = session.winnerCamp;
            this.winnerReason = session.winnerReason;
            this.currentAssassinationTargetPlayerId = session.currentAssassinationTargetPlayerId;
            this.createdAt = session.createdAt;
            this.updatedAt = session.updatedAt;
        }

        public Builder gameId(String gameId) {
            this.gameId = gameId;
            return this;
        }

        public Builder ruleSetId(String ruleSetId) {
            this.ruleSetId = ruleSetId;
            return this;
        }

        public Builder ruleSetVersion(String ruleSetVersion) {
            this.ruleSetVersion = ruleSetVersion;
            return this;
        }

        public Builder setupTemplateId(String setupTemplateId) {
            this.setupTemplateId = setupTemplateId;
            return this;
        }

        public Builder status(GameStatus status) {
            this.status = status;
            return this;
        }

        public Builder phase(GamePhase phase) {
            this.phase = phase;
            return this;
        }

        public Builder roundNo(Integer roundNo) {
            this.roundNo = roundNo;
            return this;
        }

        public Builder failedTeamVoteCount(Integer failedTeamVoteCount) {
            this.failedTeamVoteCount = failedTeamVoteCount;
            return this;
        }

        public Builder randomSeed(Long randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public Builder currentLeaderSeat(Integer currentLeaderSeat) {
            this.currentLeaderSeat = currentLeaderSeat;
            return this;
        }

        public Builder successfulMissionCount(Integer successfulMissionCount) {
            this.successfulMissionCount = successfulMissionCount;
            return this;
        }

        public Builder failedMissionCount(Integer failedMissionCount) {
            this.failedMissionCount = failedMissionCount;
            return this;
        }

        public Builder currentTeamPlayerIds(List<String> currentTeamPlayerIds) {
            this.currentTeamPlayerIds = currentTeamPlayerIds;
            return this;
        }

        public Builder currentTeamVotes(Map<String, VoteChoice> currentTeamVotes) {
            this.currentTeamVotes = currentTeamVotes;
            return this;
        }

        public Builder currentMissionChoices(Map<String, MissionChoice> currentMissionChoices) {
            this.currentMissionChoices = currentMissionChoices;
            return this;
        }

        public Builder winnerCamp(Camp winnerCamp) {
            this.winnerCamp = winnerCamp;
            return this;
        }

        public Builder winnerReason(String winnerReason) {
            this.winnerReason = winnerReason;
            return this;
        }

        public Builder currentAssassinationTargetPlayerId(String currentAssassinationTargetPlayerId) {
            this.currentAssassinationTargetPlayerId = currentAssassinationTargetPlayerId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public GameSession build() {
            return new GameSession(this);
        }
    }
}

