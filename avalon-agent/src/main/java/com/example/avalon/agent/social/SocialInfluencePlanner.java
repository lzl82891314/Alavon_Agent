package com.example.avalon.agent.social;

import com.example.avalon.agent.model.AgentTurnRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SocialInfluencePlanner {
    private static final Set<String> PUBLIC_SCOPES = Set.of("PUBLIC_CLAIM", "WORLD_FACT");
    private static final String PUBLIC_SPEECH = "PUBLIC_SPEECH";

    public SocialInfluencePlan plan(AgentTurnRequest request) {
        if (request == null || !request.getAllowedActions().contains(PUBLIC_SPEECH)) {
            return SocialInfluencePlan.empty();
        }

        List<PublicEvent> events = publicEvents(request.getObservationDelta());
        Map<String, PlayerProfile> players = publicPlayers(request.getPublicState());
        if (events.isEmpty() || players.isEmpty()) {
            return SocialInfluencePlan.empty();
        }

        String leader = leader(request.getPublicState(), players);
        Map<String, Integer> influence = influenceScores(events);
        Map<String, List<Long>> evidence = evidenceByPlayer(events);
        List<AudienceTarget> targets = selectTargets(players, leader, influence, evidence);
        if (targets.isEmpty()) {
            return SocialInfluencePlan.empty();
        }

        List<ExpectedReaction> reactions = targets.stream()
                .map(target -> expectedReaction(target, leader))
                .toList();
        List<String> followUps = targets.stream()
                .map(target -> followUp(target, leader))
                .toList();
        List<Long> basis = events.stream().map(PublicEvent::sequence).toList();
        RoleClaimCandidate claim = claimCandidate(request, targets, basis, events);
        return new SocialInfluencePlan(targets, basis, reactions, followUps, claim,
                accusationResponsePlan(request, events), observedAudienceFeedback(request, events));
    }

    private List<PublicEvent> publicEvents(List<Map<String, Object>> rawEvents) {
        List<PublicEvent> events = new ArrayList<>();
        for (Map<String, Object> raw : rawEvents) {
            if (raw == null || !PUBLIC_SCOPES.contains(string(raw.get("scope")))) continue;
            Long sequence = number(raw.get("sequence"));
            String actor = string(raw.get("actorPlayerId"));
            if (sequence != null && !actor.isBlank()) {
                events.add(new PublicEvent(sequence, string(raw.get("eventType")), actor,
                        strings(raw.get("mentions")), longs(raw.get("replyToEventSequences")),
                        string(raw.get("speechAct"))));
            }
        }
        return events;
    }

    private Map<String, PlayerProfile> publicPlayers(Map<String, Object> publicState) {
        Map<String, PlayerProfile> players = new LinkedHashMap<>();
        Object rawPlayers = publicState.get("players");
        if (!(rawPlayers instanceof List<?> values)) return players;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> raw)) continue;
            String playerId = string(raw.get("playerId"));
            Integer seat = integer(raw.get("seatNo"));
            if (!playerId.isBlank() && seat != null) players.put(playerId, new PlayerProfile(playerId, seat));
        }
        return players;
    }

    private String leader(Map<String, Object> publicState, Map<String, PlayerProfile> players) {
        Integer leaderSeat = integer(publicState.get("currentLeaderSeat"));
        return players.values().stream()
                .filter(player -> player.seatNo().equals(leaderSeat))
                .map(PlayerProfile::playerId)
                .findFirst()
                .orElse("");
    }

    private Map<String, Integer> influenceScores(List<PublicEvent> events) {
        Map<String, Integer> scores = new HashMap<>();
        for (PublicEvent event : events) {
            int weight = switch (event.eventType()) {
                case "PLAYER_ACTION" -> 2;
                case "TEAM_PROPOSED" -> 3;
                case "TEAM_VOTE_CAST" -> 1;
                default -> 0;
            };
            if (weight > 0) scores.merge(event.actorPlayerId(), weight, Integer::sum);
        }
        return scores;
    }

    private Map<String, List<Long>> evidenceByPlayer(List<PublicEvent> events) {
        Map<String, List<Long>> evidence = new HashMap<>();
        for (PublicEvent event : events) {
            evidence.computeIfAbsent(event.actorPlayerId(), ignored -> new ArrayList<>()).add(event.sequence());
        }
        return evidence;
    }

    private List<AudienceTarget> selectTargets(Map<String, PlayerProfile> players, String leader,
                                                Map<String, Integer> influence,
                                                Map<String, List<Long>> evidence) {
        List<AudienceTarget> targets = new ArrayList<>();
        if (players.containsKey(leader)) {
            targets.add(target(leader, "LEADER", 0.75, 1.0, evidence.getOrDefault(leader, List.of())));
        }
        players.values().stream()
                .filter(player -> !player.playerId().equals(leader))
                .sorted(Comparator.comparingInt((PlayerProfile player) -> influence.getOrDefault(player.playerId(), 0))
                        .reversed().thenComparing(PlayerProfile::seatNo))
                .limit(2)
                .forEach(player -> targets.add(target(player.playerId(), influence.getOrDefault(player.playerId(), 0) >= 3
                        ? "HIGH_INFLUENCE" : "SWING", Math.min(0.9, 0.45 + influence.getOrDefault(player.playerId(), 0) * 0.1),
                        influence.getOrDefault(player.playerId(), 0) >= 3 ? 0.8 : 0.55,
                        evidence.getOrDefault(player.playerId(), List.of()))));
        return new ArrayList<>(new LinkedHashSet<>(targets));
    }

    private AudienceTarget target(String playerId, String kind, double influenceability, double leverage,
                                  List<Long> evidence) {
        return new AudienceTarget(playerId, kind, influenceability, leverage, List.copyOf(evidence));
    }

    private ExpectedReaction expectedReaction(AudienceTarget target, String leader) {
        String reaction = "LEADER".equals(target.audienceKind())
                ? "重新说明队伍依据并明确公开投票倾向"
                : "回应当前队伍争议并公开确认或调整立场";
        return new ExpectedReaction(target.audience(), reaction, target.publicBasis());
    }

    private String followUp(AudienceTarget target, String leader) {
        return "LEADER".equals(target.audienceKind())
                ? target.audience() + " 是否改变下一支队伍提案或投票方向"
                : target.audience() + " 是否复述、反驳或忽略该叙事并改变投票方向";
    }

    private RoleClaimCandidate claimCandidate(AgentTurnRequest request, List<AudienceTarget> targets,
                                              List<Long> basis, List<PublicEvent> events) {
        if (events.size() < 3 || targets.size() < 2 || !"DISCUSSION".equals(request.getPhase())
                || "MERLIN".equalsIgnoreCase(request.getRoleId())) return null;
        return new RoleClaimCandidate("PERCIVAL", "测试关键受众对身份叙事的反应并迫使其公开立场",
                targets.stream().map(AudienceTarget::audience).toList(),
                List.of("目标受众要求解释身份依据", "队长或高影响玩家改变公开投票立场"),
                "若声明被质疑，转为仅依据公开投票和队伍行为分析，不继续坚持身份。",
                0.7, basis);
    }

    private AccusationResponsePlan accusationResponsePlan(AgentTurnRequest request, List<PublicEvent> events) {
        List<PublicEvent> accusations = events.stream()
                .filter(event -> !request.getPlayerId().equals(event.actorPlayerId()))
                .filter(event -> event.mentions().contains(request.getPlayerId()))
                .filter(event -> "CHALLENGE".equals(event.speechAct())
                        || referencesDirective(request, event.sequence()))
                .toList();
        if (accusations.isEmpty()) return null;

        PublicEvent latest = accusations.get(accusations.size() - 1);
        List<Long> basis = List.of(latest.sequence());
        return new AccusationResponsePlan(latest.actorPlayerId(), latest.sequence(),
                List.of(
                        responseOption("DIRECT_DENIAL", "明确否定不能由公开证据支持的结论，并给出替代解释", basis),
                        responseOption("EVIDENCE_REBUTTAL", "逐项回应可见证据，说明其为何不足以唯一支持指控", basis),
                        responseOption("LIMITED_CONCESSION", "只承认可验证的局部行为或表述，不承认未被事实支持的身份结论", basis),
                        responseOption("FOCUS_REDIRECTION", "将讨论转向同一证据下可比较的队伍、投票或其他解释", basis),
                        responseOption("REASONED_SILENCE", "仅在当前阶段不允许或没有新的公开回应空间时，说明暂不扩展争论的可验证理由", basis)),
                List.of("指控者是否补充证据、修正结论或改变投票", "关键受众是否接受替代解释并调整公开立场"));
    }

    private AudienceFeedback observedAudienceFeedback(AgentTurnRequest request, List<PublicEvent> events) {
        Object previous = request.getMemory().get("communicationPlan");
        if (!(previous instanceof Map<?, ?> plan)) return AudienceFeedback.empty();
        List<String> intended = strings(plan.get("targetAudience"));
        if (intended.isEmpty()) intended = strings(plan.get("desiredAudienceBeliefs"));
        if (intended.isEmpty()) return AudienceFeedback.empty();
        List<String> audience = List.copyOf(intended);
        List<PublicEvent> reactions = events.stream()
                .filter(event -> audience.contains(event.actorPlayerId()))
                .filter(event -> !event.replyToEventSequences().isEmpty())
                .toList();
        return new AudienceFeedback(audience, reactions.stream().map(PublicEvent::sequence).toList(),
                reactions.isEmpty() ? "NO_OBSERVABLE_REACTION" : "PUBLIC_REACTION_OBSERVED",
                "仅记录公开回复和后续公开立场；不从沉默推断私有身份。");
    }

    private boolean referencesDirective(AgentTurnRequest request, long sequence) {
        Object reply = request.getDiscussionDirective().get("replyToEventSequence");
        return reply instanceof Number number && number.longValue() == sequence;
    }

    private ResponseOption responseOption(String strategy, String purpose, List<Long> evidence) {
        return new ResponseOption(strategy, purpose, evidence);
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
    }

    private static List<Long> longs(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(Number.class::isInstance).map(Number.class::cast)
                .map(Number::longValue).toList();
    }

    private record PublicEvent(long sequence, String eventType, String actorPlayerId, List<String> mentions,
                               List<Long> replyToEventSequences, String speechAct) { }

    private record PlayerProfile(String playerId, Integer seatNo) { }

    public record AudienceTarget(String audience, String audienceKind, double influenceability,
                                 double decisionLeverage, List<Long> publicBasis) {
        public AudienceTarget {
            publicBasis = publicBasis == null ? List.of() : List.copyOf(publicBasis);
        }
    }

    public record ExpectedReaction(String audience, String expectedResponse, List<Long> publicBasis) {
        public ExpectedReaction {
            publicBasis = publicBasis == null ? List.of() : List.copyOf(publicBasis);
        }
    }

    public record RoleClaimCandidate(String claimedRole, String strategicPurpose, List<String> targetAudience,
                                     List<String> expectedReactions, String exitNarrative, double assessedRisk,
                                     List<Long> publicBasis) {
        public RoleClaimCandidate {
            targetAudience = targetAudience == null ? List.of() : List.copyOf(targetAudience);
            expectedReactions = expectedReactions == null ? List.of() : List.copyOf(expectedReactions);
            publicBasis = publicBasis == null ? List.of() : List.copyOf(publicBasis);
        }
    }

    public record SocialInfluencePlan(List<AudienceTarget> targetAudiences, List<Long> publicBasisSequences,
                                      List<ExpectedReaction> expectedReactions,
                                      List<String> followUpObservationPoints,
                                      RoleClaimCandidate highRiskRoleClaim,
                                      AccusationResponsePlan accusationResponsePlan,
                                      AudienceFeedback observedAudienceFeedback) {
        public SocialInfluencePlan {
            targetAudiences = targetAudiences == null ? List.of() : List.copyOf(targetAudiences);
            publicBasisSequences = publicBasisSequences == null ? List.of() : List.copyOf(publicBasisSequences);
            expectedReactions = expectedReactions == null ? List.of() : List.copyOf(expectedReactions);
            followUpObservationPoints = followUpObservationPoints == null ? List.of() : List.copyOf(followUpObservationPoints);
        }

        public static SocialInfluencePlan empty() {
            return new SocialInfluencePlan(List.of(), List.of(), List.of(), List.of(), null, null, AudienceFeedback.empty());
        }
    }

    public record ResponseOption(String strategy, String purpose, List<Long> publicBasis) {
        public ResponseOption {
            publicBasis = publicBasis == null ? List.of() : List.copyOf(publicBasis);
        }
    }

    public record AccusationResponsePlan(String accuser, long accusationSequence,
                                         List<ResponseOption> responseOptions,
                                         List<String> followUpObservations) {
        public AccusationResponsePlan {
            responseOptions = responseOptions == null ? List.of() : List.copyOf(responseOptions);
            followUpObservations = followUpObservations == null ? List.of() : List.copyOf(followUpObservations);
        }
    }

    public record AudienceFeedback(List<String> intendedAudience, List<Long> observedReactionSequences,
                                   String status, String interpretationBoundary) {
        public AudienceFeedback {
            intendedAudience = intendedAudience == null ? List.of() : List.copyOf(intendedAudience);
            observedReactionSequences = observedReactionSequences == null ? List.of() : List.copyOf(observedReactionSequences);
        }

        public static AudienceFeedback empty() {
            return new AudienceFeedback(List.of(), List.of(), "NOT_APPLICABLE",
                    "没有已持久化的公开受众计划可供反馈。");
        }
    }
}
