package com.example.avalon.agent.strategy;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared role policy used by prompt projection and host validation. */
public final class RoleStrategyPolicy {
    private RoleStrategyPolicy() {
    }

    public static List<String> permittedDeceptionIntents(String role) {
        String normalized = normalize(role);
        return switch (normalized) {
            case "MERLIN" -> List.of("NONE", "WITHHOLD_PRIVATE_KNOWLEDGE", "UNDERSTATE_CONFIDENCE");
            case "PERCIVAL" -> List.of("NONE", "WITHHOLD_PRIVATE_KNOWLEDGE", "BAIT_ASSASSIN");
            case "MORGANA" -> List.of("NONE", "OVERSTATE_SUSPICION", "CREATE_ALTERNATIVE_EXPLANATION", "DISTANCE_FROM_TEAMMATE", "BUILD_FALSE_CREDIBILITY");
            case "ASSASSIN", "MORDRED" -> List.of("NONE", "OVERSTATE_SUSPICION", "CREATE_ALTERNATIVE_EXPLANATION", "DISTANCE_FROM_TEAMMATE");
            case "OBERON" -> List.of("NONE", "OVERSTATE_SUSPICION", "CREATE_ALTERNATIVE_EXPLANATION", "BUILD_FALSE_CREDIBILITY");
            default -> List.of("NONE");
        };
    }

    public static List<String> strategyModes(String role) {
        return modeDefinitions(normalize(role)).stream().map(StrategyModeDefinition::mode).toList();
    }

    public static List<Map<String, Object>> strategyModeContracts(String role, List<String> candidates) {
        return modeDefinitions(normalize(role)).stream()
                .filter(definition -> candidates.contains(definition.mode()))
                .map(StrategyModeDefinition::asMap)
                .toList();
    }

    private static List<StrategyModeDefinition> modeDefinitions(String role) {
        return switch (role) {
            case "MERLIN" -> List.of(
                    mode("GUIDE_WITH_PUBLIC_COVER", "public evidence can justify useful guidance", "improve good-team quality without exposing private certainty", "reduce directiveness when public cover no longer supports it"),
                    mode("CONTROLLED_UNCERTAINTY", "correct guidance needs credible uncertainty", "preserve multiple public explanations while still helping good", "leave this mode when uncertainty starts harming mission quality"),
                    mode("DECOY_SUPPORT", "another good player can safely carry visible leadership", "support a plausible public leader without privately confirming them", "withdraw when the decoy becomes unreliable or endangered"),
                    mode("PROTECT_GOOD_TEAM", "the proposed or upcoming mission is strategically important", "prioritize a viable good mission team", "reassess after the vote or mission outcome"),
                    mode("REDUCE_EXPOSURE", "recent behavior may reveal an unexplained knowledge advantage", "lower assassination exposure without sacrificing clear camp value", "exit when exposure is controlled or mission risk becomes urgent"),
                    mode("EMERGENCY_DIRECTION", "evil is near a mission victory or rejection pressure is critical", "use the strongest public case available to protect the good win line", "return to covered guidance after the immediate danger"));
            case "PERCIVAL" -> List.of(
                    mode("CANDIDATE_DISCRIMINATION", "Merlin and Morgana candidates remain unresolved", "seek observations that distinguish competing assignments", "exit only when evidence materially favors one hypothesis or the distinction loses value"),
                    mode("PROTECT_MERLIN", "a leading Merlin candidate faces avoidable exposure or exclusion", "protect the candidate without publicly confirming the role", "reassess when the candidate ranking changes"),
                    mode("BAIT_ASSASSIN", "good can afford controlled identity pressure and a follow-up exists", "draw attention away from the likely Merlin candidate", "exit if the bait harms team coordination or becomes implausible"),
                    mode("FALSE_PERCIVAL_CLAIM", "a deliberate public-role narrative has explicit audience, risk, and exit plan", "use a claim only as a measurable information or protection probe", "abandon or explain the claim according to its recorded exit narrative"),
                    mode("CONTROL_PUBLIC_FOCUS", "discussion is converging on the wrong special-role signal", "redirect scrutiny toward publicly testable behavior", "exit after the relevant players commit or respond"));
            case "MORGANA" -> List.of(
                    mode("BUILD_MERLIN_CREDIBILITY", "Percival ambiguity can still be influenced", "appear plausibly informed without relying on impossible knowledge", "switch when the persona creates more suspicion than influence"),
                    mode("PARTIAL_TRUTH_LEADERSHIP", "accurate public analysis can earn durable influence", "mix defensible guidance with a coherent evil win line", "reduce leadership when accuracy becomes identity-revealing"),
                    mode("MISDIRECT_PERCIVAL", "a likely Percival or Merlin/Morgana comparison is visible", "create competing explanations around the special-role candidates", "exit when the target no longer reacts or the narrative is disproved"),
                    mode("DISTANCE_FROM_TEAMMATE", "public evidence makes visible alignment costly", "preserve independent credibility without mechanically attacking allies", "stop when distance no longer changes audience beliefs"),
                    mode("CONTROLLED_SABOTAGE", "mission state permits a deliberate success/fail trade-off", "compare mission damage against role and coordination exposure", "reassess after the mission result"),
                    mode("FALSE_ROLE_CLAIM", "a role claim has a defined audience, payoff, consistency burden, and exit", "use identity pressure to distort good-role coordination", "exit through the preplanned narrative when cost exceeds benefit"));
            case "ASSASSIN" -> List.of(
                    mode("MERLIN_TRACKING", "the game has started and Merlin remains unidentified", "maintain competing Merlin, Percival, and bait candidates", "only resolve at assassination commit or game end"),
                    mode("LOW_VISIBILITY_SABOTAGE", "mission damage is valuable but visibility must be preserved", "choose mission pressure while retaining assassination information", "reassess after the mission result"),
                    mode("TEAMMATE_COVER", "an ally remains strategically valuable and cover is publicly defensible", "protect the evil win line without revealing coordination", "withdraw when cover creates stronger association evidence"),
                    mode("TEAMMATE_SACRIFICE", "an ally is already costly and sacrifice improves the remaining win line", "convert public pressure into credibility or Merlin information", "stop once the strategic payoff is realized"),
                    mode("PROBE_ACCURATE_PLAYERS", "some players guide outcomes beyond their public evidence", "test whether accuracy indicates Merlin, Percival, or deliberate bait", "re-rank after the targeted response or outcome"),
                    mode("ASSASSINATION_COMMIT", "the rules permit assassination", "compare at least two candidates before selecting a target", "terminal after a legal assassination action"));
            case "LOYAL_SERVANT" -> List.of(
                    mode("INFORMATION_SEEKING", "public evidence cannot yet separate leading hypotheses", "choose actions that create useful public evidence", "exit when mission security or a clearer hypothesis dominates"),
                    mode("COALITION_BUILDING", "several publicly credible players can coordinate a viable team", "form a testable voting and mission coalition", "reassess after a member contradicts the coalition basis"),
                    mode("VOTE_PATTERN_PRESSURE", "votes diverge from public positions or prior patterns", "force explanations for observable voting behavior", "exit after commitments are clarified or disproved"),
                    mode("LEADER_ACCOUNTABILITY", "a leader proposal needs public justification", "make the leader state testable selection principles", "exit after the proposal is resolved"),
                    mode("IDENTITY_BAIT", "controlled attention can protect special roles and has a safe exit", "probe reactions without claiming private certainty", "exit when coordination cost exceeds information value"),
                    mode("MISSION_SECURITY", "evil is near a mission victory or the mission is decisive", "prioritize the strongest publicly supported good-team line", "reassess after the decisive pressure passes"));
            case "MORDRED", "OBERON" -> List.of(
                    mode("INFILTRATE_TEAM", "publicly defensible team access is available", "gain mission influence without unsupported identity claims", "switch when access becomes association evidence"),
                    mode("CONTROLLED_SABOTAGE", "mission state permits a success/fail trade-off", "compare immediate mission damage with exposure and future access", "reassess after the mission result"),
                    mode("DISTANCE_FROM_TEAMMATE", "known or inferred evil alignment is becoming publicly costly", "preserve independent credibility within actual private knowledge limits", "stop when distancing no longer improves the win line"),
                    mode("FRAME_GOOD_PLAYER", "public contradictions support more than one explanation", "promote a defensible alternative explanation", "withdraw when contrary evidence makes it incoherent"),
                    mode("FORCE_BAD_CHOICE", "good players face two publicly plausible but strategically unequal options", "shape the choice toward the evil win line", "reassess once players commit"),
                    mode("DESPERATION_PLAY", "good is one mission from victory and lower-risk lines are insufficient", "accept measured exposure for a credible remaining evil win line", "exit after the immediate decisive action"));
            default -> List.of(
                    mode("EVIDENCE_REVIEW", "the current role has no specialized policy", "advance the camp objective using visible evidence", "reassess when role-specific policy becomes available"));
        };
    }

    private static StrategyModeDefinition mode(String mode,
                                               String entryCondition,
                                               String primaryObjective,
                                               String exitCondition) {
        return new StrategyModeDefinition(mode, entryCondition, primaryObjective, exitCondition,
                "return to evidence review and preserve unresolved alternatives");
    }

    private static String normalize(String role) {
        return role == null ? "UNKNOWN" : role.trim().toUpperCase(Locale.ROOT);
    }

    private record StrategyModeDefinition(String mode,
                                          String entryCondition,
                                          String primaryObjective,
                                          String exitCondition,
                                          String recovery) {
        private Map<String, Object> asMap() {
            return Map.of(
                    "mode", mode,
                    "entryCondition", entryCondition,
                    "primaryObjective", primaryObjective,
                    "exitCondition", exitCondition,
                    "recovery", recovery);
        }
    }
}
