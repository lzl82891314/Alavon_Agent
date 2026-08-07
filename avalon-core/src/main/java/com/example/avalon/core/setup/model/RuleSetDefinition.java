package com.example.avalon.core.setup.model;

import java.util.List;
import java.util.Map;

public record RuleSetDefinition(
        String ruleSetId,
        String name,
        String version,
        Integer minPlayers,
        Integer maxPlayers,
        List<RoundTeamSizeRule> teamSizeRules,
        Map<Integer, Integer> failThresholdByRound,
        List<String> supportedSetupTemplateIds,
        AssassinationRuleDefinition assassinationRule,
        VisibilityPolicyDefinition visibilityPolicy,
        boolean randomAssignment
) {
    public RuleSetDefinition {
        teamSizeRules = teamSizeRules == null ? List.of() : List.copyOf(teamSizeRules);
        failThresholdByRound = failThresholdByRound == null ? Map.of() : Map.copyOf(failThresholdByRound);
        supportedSetupTemplateIds = supportedSetupTemplateIds == null ? List.of() : List.copyOf(supportedSetupTemplateIds);
    }

    public int teamSizeForRound(int roundNo) {
        return teamSizeRules.stream()
                .filter(rule -> rule.round() == roundNo)
                .findFirst()
                .map(RoundTeamSizeRule::teamSize)
                .orElseThrow(() -> new IllegalArgumentException("Missing team size rule for round " + roundNo));
    }

    public int failThresholdForRound(int roundNo) {
        Integer threshold = failThresholdByRound.get(roundNo);
        if (threshold == null) {
            throw new IllegalArgumentException("Missing fail threshold rule for round " + roundNo);
        }
        return threshold;
    }

    public boolean supportsSetupTemplate(String templateId) {
        return supportedSetupTemplateIds.contains(templateId);
    }
}

