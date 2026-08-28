package com.example.avalon.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public final class EvaluateTeamCombinationsTool implements AgentTool {
    private static final ToolDescriptor DESCRIPTOR = StrategicToolSupport.descriptor(
            "evaluate_team_combinations",
            "Compare candidate teams using visible evidence, possible-world coverage, and precomputed legal strategic candidates.",
            Map.of("candidateTeams", Map.of("type", "array", "items", Map.of("type", "array",
                    "items", Map.of("type", "string")))), "candidateTeams");

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, ToolCall call) {
        StrategicToolSupport.rejectUnknownArguments(call.arguments(), "candidateTeams");
        List<List<String>> requestedTeams = candidateTeams(call.arguments().get("candidateTeams"));
        validateTeams(context, requestedTeams);
        List<Map<String, Object>> hostCandidates = StrategicToolSupport.maps(
                context.request().getStrategyContext().get("teamCandidates"));
        List<Map<String, Object>> actionCandidates = StrategicToolSupport.maps(
                        context.request().getStrategyContext().get("actionCandidates")).stream()
                .filter(item -> "TEAM_PROPOSAL".equals(item.get("actionType"))).toList();
        List<Map<String, Object>> worlds = StrategicToolSupport.maps(
                context.request().getStrategyContext().get("possibleWorlds"));
        List<Map<String, Object>> evidence = StrategicToolSupport.maps(
                context.request().getStrategyContext().get("evidenceAssessments"));
        List<Map<String, Object>> all = java.util.stream.Stream.of(hostCandidates, actionCandidates, evidence)
                .flatMap(List::stream).toList();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("requestedTeams", requestedTeams);
        content.put("hostCandidateEvidence", hostCandidates);
        content.put("actionAssessments", actionCandidates);
        content.put("possibleWorlds", worlds);
        content.put("predictionFeedback", context.request().getStrategyContext().getOrDefault(
                "predictionFeedback", List.of()));
        content.put("rolePolicy", rolePolicy(context));
        content.put("audiencePlan", context.request().getStrategyContext().getOrDefault("audiencePlan", Map.of()));
        content.put("comparisonGuidance", Map.of(
                "worldDiscrimination", "Prefer teams whose outcome distinguishes plausible worlds.",
                "campValue", "Compare likely mission value under each retained world.",
                "exposureCost", "Account for role-specific information exposure.",
                "followUp", "State which public vote or mission outcome will update the hypothesis."));
        content.put("sourceSequences", StrategicToolSupport.sequences(all));
        return ToolResult.success(call, content, StrategicToolSupport.sequences(all));
    }

    private Map<String, Object> rolePolicy(ToolExecutionContext context) {
        Map<String, Object> strategy = context.request().getStrategyContext();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("policyId", "role", "objectives", "objectiveWeights", "riskBudget",
                "constraints", "permittedDeceptionIntents", "modeCandidates", "decisionQuestions",
                "competingRoleHypotheses", "worldEvaluationGuidance", "behaviorPredictions",
                "assassinationTracking")) {
            if (strategy.containsKey(key)) result.put(key, strategy.get(key));
        }
        return Map.copyOf(result);
    }

    private List<List<String>> candidateTeams(Object value) {
        if (value == null) throw new IllegalArgumentException("candidateTeams is required");
        if (!(value instanceof Collection<?> teams)) throw new IllegalArgumentException("candidateTeams must be an array");
        return teams.stream().map(team -> {
            if (!(team instanceof Collection<?> players) || players.stream().anyMatch(player -> !(player instanceof String))) {
                throw new IllegalArgumentException("Each candidate team must be an array of player ids");
            }
            return players.stream().map(String.class::cast).toList();
        }).toList();
    }

    private void validateTeams(ToolExecutionContext context, List<List<String>> teams) {
        Set<String> known = StrategicToolSupport.maps(context.request().getPublicState().get("players")).stream()
                .map(player -> String.valueOf(player.get("playerId"))).collect(java.util.stream.Collectors.toSet());
        int expectedSize = context.request().getPublicState().get("teamSize") instanceof Number number
                ? number.intValue() : 0;
        for (List<String> team : teams) {
            if (team.size() != Set.copyOf(team).size()) throw new IllegalArgumentException("Candidate team contains duplicate players");
            if (!known.containsAll(team)) throw new IllegalArgumentException("Candidate team contains an unknown player id");
            if (expectedSize > 0 && team.size() != expectedSize) {
                throw new IllegalArgumentException("Candidate team size must be " + expectedSize);
            }
        }
    }
}
