package com.example.avalon.agent.strategy;

import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.PublicGameSnapshot;
import com.example.avalon.core.player.memory.VisiblePlayerInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Produces enforceable role policy input; it never chooses an action for the model. */
@Component
public final class RoleStrategyPlanner {
    public Map<String, Object> plan(PlayerTurnContext context, PlayerAgentConfig profile) {
        String role = normalize(context.roleId());
        List<String> modeCandidates = modeCandidates(role, context);
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("policyId", "role-" + role.toLowerCase(Locale.ROOT) + "-v2");
        policy.put("role", role);
        policy.put("camp", context.privateView().camp().name());
        policy.put("situation", situation(context));
        policy.put("objectives", objectives(role, context));
        policy.put("constraints", constraints(role));
        policy.put("permittedDeceptionIntents", RoleStrategyPolicy.permittedDeceptionIntents(role));
        policy.put("allowedStrategyModes", RoleStrategyPolicy.strategyModes(role));
        policy.put("strategyModeContracts", RoleStrategyPolicy.strategyModeContracts(role, modeCandidates));
        policy.put("modeCandidates", modeCandidates);
        policy.put("decisionQuestions", decisionQuestions(role, context));
        policy.put("competingRoleHypotheses", competingRoleHypotheses(role, context));
        policy.put("behaviorPredictions", behaviorPredictions(role, context));
        policy.put("assassinationTracking", assassinationTracking(role, context));
        policy.put("existingCommitments", context.memoryState().commitments());
        policy.put("priorStrategy", context.memoryState().strategyState());
        policy.put("cognitionParameters", profile == null ? Map.of() : profile.getCognition());
        policy.put("communicationParameters", profile == null ? Map.of() : profile.getCommunication());
        policy.put("deceptionParameters", profile == null ? Map.of() : profile.getDeception());
        return policy;
    }

    private Map<String, Object> situation(PlayerTurnContext context) {
        PublicGameSnapshot state = context.publicState();
        Map<String, Object> situation = new LinkedHashMap<>();
        situation.put("phase", context.phase());
        situation.put("roundNo", context.roundNo());
        situation.put("successfulMissions", state.successfulMissionCount());
        situation.put("failedMissions", state.failedMissionCount());
        situation.put("failedTeamVotes", state.failedTeamVoteCount());
        situation.put("currentTeam", state.currentTeamPlayerIds());
        situation.put("priorMode", context.memoryState().strategyMode());
        return situation;
    }

    private List<String> objectives(String role, PlayerTurnContext context) {
        boolean goodAtRisk = missionCount(context, false) >= 2;
        boolean evilAtRisk = missionCount(context, true) >= 2;
        return switch (role) {
            case "MERLIN" -> List.of(goodAtRisk
                            ? "protect the remaining good mission line using the strongest public justification"
                            : "improve good-team quality while creating public cover for the guidance",
                    "compare mission value with assassination exposure rather than hiding by default");
            case "PERCIVAL" -> List.of("collect behavior that distinguishes the private Merlin/Morgana candidates",
                    evilAtRisk ? "prepare protection or bait without publicly confirming Merlin" : "protect likely Merlin without collapsing the competing hypotheses");
            case "MORGANA" -> List.of(evilAtRisk
                            ? "preserve the remaining evil win line while keeping Merlin-like credibility usable"
                            : "build influence through defensible partial truths and competing explanations",
                    "choose sabotage, leadership, or distance by current exposure and mission value");
            case "ASSASSIN" -> List.of("track Merlin, Percival, and deliberate bait candidates from this turn onward",
                    evilAtRisk ? "preserve both the mission and assassination win lines" : "use public reactions to distinguish accurate players from protected or baiting players");
            case "MORDRED" -> List.of("use Merlin invisibility without relying on it as permanent cover",
                    evilAtRisk ? "accept measured risk to preserve the evil mission line" : "compare infiltration, distancing, and sabotage timing");
            case "OBERON" -> List.of("infer alignments only from visible behavior and public outcomes",
                    evilAtRisk ? "seek an independent decisive evil line" : "compare infiltration, framing, and controlled sabotage");
            case "LOYAL_SERVANT" -> List.of(goodAtRisk
                            ? "prioritize mission security while retaining public accountability"
                            : "maximize information gained from teams, votes, and explicit commitments",
                    "test competing explanations instead of defaulting to identity concealment");
            default -> List.of("advance the camp objective", "use only visible evidence", "preserve uncertainty when evidence is weak");
        };
    }

    private List<String> constraints(String role) {
        return switch (role) {
            case "MERLIN" -> List.of("do not express certainty that only private knowledge supports", "track exposureRisk in strategyState");
            case "PERCIVAL" -> List.of("candidate roles are ambiguous", "do not publicly confirm a protected target");
            case "OBERON" -> List.of("do not claim knowledge of evil teammates", "do not coordinate from hidden information");
            case "MORGANA", "ASSASSIN", "MORDRED" -> List.of("do not reveal the evil roster", "explain or deliberately manage departures from public commitments");
            default -> List.of("public claims are not world facts", "large belief changes require visible evidence");
        };
    }

    private List<String> modeCandidates(String role, PlayerTurnContext context) {
        String phase = normalize(context.phase());
        boolean goodAtRisk = missionCount(context, false) >= 2;
        boolean evilAtRisk = missionCount(context, true) >= 2;
        return switch (role) {
            case "MERLIN" -> goodAtRisk
                    ? List.of("EMERGENCY_DIRECTION", "PROTECT_GOOD_TEAM", "GUIDE_WITH_PUBLIC_COVER")
                    : List.of("GUIDE_WITH_PUBLIC_COVER", "CONTROLLED_UNCERTAINTY", "PROTECT_GOOD_TEAM");
            case "PERCIVAL" -> evilAtRisk
                    ? List.of("PROTECT_MERLIN", "BAIT_ASSASSIN", "CANDIDATE_DISCRIMINATION")
                    : List.of("CANDIDATE_DISCRIMINATION", "CONTROL_PUBLIC_FOCUS", "PROTECT_MERLIN");
            case "MORGANA" -> evilAtRisk
                    ? List.of("CONTROLLED_SABOTAGE", "PARTIAL_TRUTH_LEADERSHIP", "MISDIRECT_PERCIVAL")
                    : List.of("BUILD_MERLIN_CREDIBILITY", "PARTIAL_TRUTH_LEADERSHIP", "MISDIRECT_PERCIVAL");
            case "ASSASSIN" -> "ASSASSINATION".equals(phase)
                    ? List.of("ASSASSINATION_COMMIT", "MERLIN_TRACKING", "PROBE_ACCURATE_PLAYERS")
                    : List.of("MERLIN_TRACKING", "PROBE_ACCURATE_PLAYERS",
                    "MISSION_ACTION".equals(phase) ? "LOW_VISIBILITY_SABOTAGE" : "TEAMMATE_COVER");
            case "LOYAL_SERVANT" -> goodAtRisk
                    ? List.of("MISSION_SECURITY", "VOTE_PATTERN_PRESSURE", "LEADER_ACCOUNTABILITY")
                    : List.of("INFORMATION_SEEKING", "VOTE_PATTERN_PRESSURE", "COALITION_BUILDING");
            case "MORDRED", "OBERON" -> evilAtRisk
                    ? List.of("DESPERATION_PLAY", "CONTROLLED_SABOTAGE", "FORCE_BAD_CHOICE")
                    : List.of("INFILTRATE_TEAM", "CONTROLLED_SABOTAGE", "FRAME_GOOD_PLAYER");
            default -> List.of("EVIDENCE_REVIEW");
        };
    }

    private List<String> decisionQuestions(String role, PlayerTurnContext context) {
        List<String> questions = new ArrayList<>();
        questions.add("Which visible event would change the leading explanation rather than merely restate it?");
        questions.add("Which candidate mode best fits the current phase, score, and prior strategy, and what would make it exit?");
        questions.add("Does the next action test a prediction or contradict a prior public commitment?");
        switch (role) {
            case "MERLIN" -> questions.add(missionCount(context, false) >= 2
                    ? "How much public direction is now justified by the danger to the good mission line?"
                    : "Which public reason can support useful guidance without revealing private certainty?");
            case "PERCIVAL" -> questions.add("What next proposal, vote, or explanation would differ under the two Merlin/Morgana assignments?");
            case "MORGANA" -> questions.add("Would accurate leadership, candidate misdirection, teammate distance, or controlled sabotage create the strongest future evil line now?");
            case "ASSASSIN" -> questions.add("Which public behavior distinguishes Merlin-level unexplained accuracy from Percival protection or deliberate bait?");
            case "LOYAL_SERVANT" -> questions.add(missionCount(context, false) >= 2
                    ? "Which publicly supported team best protects the decisive mission?"
                    : "Which team or vote would produce the most discriminating public evidence?");
            case "MORDRED" -> questions.add("Does invisibility make leadership useful now, or would visible control create avoidable association evidence?");
            case "OBERON" -> questions.add("Which public behavior can distinguish allies from good players without assuming hidden teammate knowledge?");
            default -> questions.add("What role-specific uncertainty remains unresolved?");
        }
        return List.copyOf(questions);
    }

    private List<Map<String, Object>> competingRoleHypotheses(String role, PlayerTurnContext context) {
        if (!"PERCIVAL".equals(role)) return List.of();
        List<VisiblePlayerInfo> candidates = context.privateView().knowledge().visiblePlayers().stream()
                .filter(player -> player.exactRoleId() == null)
                .filter(player -> normalizedRoles(player.candidateRoleIds()).containsAll(Set.of("MERLIN", "MORGANA")))
                .toList();
        if (candidates.size() != 2) return List.of();

        VisiblePlayerInfo first = candidates.get(0);
        VisiblePlayerInfo second = candidates.get(1);
        return List.of(
                roleHypothesis("PERCIVAL-H1", first.playerId(), "MERLIN", second.playerId(), "MORGANA"),
                roleHypothesis("PERCIVAL-H2", first.playerId(), "MORGANA", second.playerId(), "MERLIN"));
    }

    private Map<String, Object> roleHypothesis(String id,
                                               String firstPlayerId,
                                               String firstRole,
                                               String secondPlayerId,
                                               String secondRole) {
        Map<String, String> assignments = new LinkedHashMap<>();
        assignments.put(firstPlayerId, firstRole);
        assignments.put(secondPlayerId, secondRole);
        return Map.of(
                "hypothesisId", id,
                "candidateRoles", assignments,
                "relativeConfidence", "UNASSESSED",
                "privateBasis", "Both players are visible only as Merlin/Morgana candidates",
                "predictionSummary", candidatePrediction(firstPlayerId, firstRole, secondPlayerId));
    }

    private String candidatePrediction(String playerId, String candidateRole, String counterpartId) {
        if ("MERLIN".equals(candidateRole)) {
            return "If " + playerId + " is Merlin, compare whether proposals, votes, and explanations involving "
                    + counterpartId + " balance mission protection with plausible public cover.";
        }
        return "If " + playerId + " is Morgana, compare whether proposals, votes, and explanations involving "
                + counterpartId + " build Merlin-like credibility without implying exact knowledge of Merlin.";
    }

    private List<Map<String, Object>> behaviorPredictions(String role, PlayerTurnContext context) {
        String situation = "phase=" + context.phase() + ", round=" + context.roundNo()
                + ", score=" + missionCount(context, true) + " good successes to "
                + missionCount(context, false) + " evil successes";
        return switch (role) {
            case "PERCIVAL" -> competingRoleHypotheses(role, context).stream()
                    .map(hypothesis -> Map.<String, Object>of(
                            "hypothesisId", hypothesis.get("hypothesisId"),
                            "status", "PENDING",
                            "situation", situation,
                            "expectedBehavior", hypothesis.get("predictionSummary"),
                            "checkAfter", List.of("next team proposal", "revealed team vote", "public explanation involving either candidate"),
                            "resultLabels", List.of("SUPPORTED", "CONTRADICTED", "INCONCLUSIVE")))
                    .toList();
            case "MERLIN" -> List.of(prediction(situation,
                    "Compare whether covered guidance improves team quality without requiring private certainty in public.",
                    "next proposal, vote, or mission outcome"));
            case "MORGANA" -> List.of(prediction(situation,
                    "Compare whether the selected persona changes Percival-candidate treatment or public trust without creating impossible knowledge claims.",
                    "targeted responses and the next revealed vote"));
            case "ASSASSIN" -> List.of(prediction(situation,
                    "Compare whether an accurate player's next guidance remains stronger than their cited public evidence, while separating protection and bait explanations.",
                    "next proposal, public challenge, vote, or mission result"));
            case "LOYAL_SERVANT" -> List.of(prediction(situation,
                    "Compare whether the chosen information probe causes players to make distinguishable public commitments.",
                    "next public commitments and revealed votes"));
            case "MORDRED", "OBERON" -> List.of(prediction(situation,
                    "Compare whether the selected infiltration or pressure line changes team access without creating stronger public association evidence.",
                    "next proposal and revealed vote"));
            default -> List.of();
        };
    }

    private Map<String, Object> prediction(String situation, String expectedBehavior, String checkAfter) {
        return Map.of(
                "status", "PENDING",
                "situation", situation,
                "expectedBehavior", expectedBehavior,
                "checkAfter", checkAfter,
                "resultLabels", List.of("SUPPORTED", "CONTRADICTED", "INCONCLUSIVE"));
    }

    private Map<String, Object> assassinationTracking(String role, PlayerTurnContext context) {
        if (!"ASSASSIN".equals(role)) return Map.of();
        Set<String> knownEvil = context.privateView().knowledge().visiblePlayers().stream()
                .filter(player -> player.camp() == Camp.EVIL)
                .map(VisiblePlayerInfo::playerId)
                .collect(java.util.stream.Collectors.toSet());
        List<String> candidates = context.publicState().players().stream()
                .map(player -> player.playerId())
                .filter(playerId -> !Objects.equals(playerId, context.playerId()))
                .filter(playerId -> !knownEvil.contains(playerId))
                .toList();
        return Map.of(
                "activeFromRound", 1,
                "candidatePool", candidates,
                "dimensions", List.of("unexplainedAccuracy", "influenceOnGoodTeams", "leadershipVisibility",
                        "percivalProtectionLikelihood", "deliberateBaitLikelihood", "publicReasonQuality"),
                "updateQuestions", List.of(
                        "Who was accurate before public evidence justified confidence?",
                        "Who improved good teams while avoiding visible leadership?",
                        "Who may be Percival protecting another player or deliberately baiting assassination?"),
                "priorTracking", context.memoryState().strategyState().getOrDefault("assassinationTracking", Map.of()),
                "selectionRule", "Keep competing candidates; do not treat any dimension as authoritative identity proof");
    }

    private int missionCount(PlayerTurnContext context, boolean successful) {
        Integer count = successful
                ? context.publicState().successfulMissionCount()
                : context.publicState().failedMissionCount();
        return count == null ? 0 : count;
    }

    private Set<String> normalizedRoles(List<String> roles) {
        return roles.stream().map(this::normalize).collect(java.util.stream.Collectors.toSet());
    }

    private String normalize(String role) {
        return role == null ? "UNKNOWN" : role.trim().toUpperCase(Locale.ROOT);
    }
}
