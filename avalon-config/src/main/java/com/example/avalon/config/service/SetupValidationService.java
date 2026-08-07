package com.example.avalon.config.service;

import com.example.avalon.config.exception.ConfigValidationException;
import com.example.avalon.config.model.AvalonConfigRegistry;
import com.example.avalon.config.model.LlmModelDefinition;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.RoundTeamSizeRule;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SetupValidationService {
    public void validate(AvalonConfigRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        for (RuleSetDefinition ruleSet : registry.ruleSets()) {
            if (ruleSet.minPlayers() <= 0) {
                throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " must define positive minPlayers");
            }
            if (ruleSet.maxPlayers() < ruleSet.minPlayers()) {
                throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " has maxPlayers < minPlayers");
            }
            if (ruleSet.supportedSetupTemplateIds().isEmpty()) {
                throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " must support at least one setup template");
            }
            if (ruleSet.teamSizeRules().isEmpty()) {
                throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " must define team size rules");
            }
            validateTeamSizeRules(ruleSet);
            validateMissionThresholds(ruleSet);
            validateAssassinationRule(ruleSet, registry);
        }

        for (SetupTemplate template : registry.setupTemplates()) {
            validateSetupTemplate(template, registry);
        }

        for (LlmModelDefinition modelProfile : registry.modelProfiles()) {
            validateModelProfile(modelProfile);
        }
    }

    public void validate(RuleSetDefinition ruleSet, SetupTemplate template, Map<String, RoleDefinition> roles) {
        Objects.requireNonNull(ruleSet, "ruleSet");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(roles, "roles");

        if (!ruleSet.supportsSetupTemplate(template.templateId())) {
            throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " does not support template " + template.templateId());
        }
        if (template.playerCount() < ruleSet.minPlayers() || template.playerCount() > ruleSet.maxPlayers()) {
            throw new ConfigValidationException("Template " + template.templateId() + " player count is outside rule set bounds");
        }
        if (template.roleIds().size() != template.playerCount()) {
            throw new ConfigValidationException("Template " + template.templateId() + " role count must equal player count");
        }
        for (String roleId : template.roleIds()) {
            if (!roles.containsKey(roleId)) {
                throw new ConfigValidationException("Template " + template.templateId() + " references missing role " + roleId);
            }
        }
    }

    private void validateSetupTemplate(SetupTemplate template, AvalonConfigRegistry registry) {
        if (!template.enabled()) {
            throw new ConfigValidationException("Setup template " + template.templateId() + " is disabled");
        }
        RuleSetDefinition ruleSet = registry.ruleSets().stream()
                .filter(candidate -> candidate.supportsSetupTemplate(template.templateId()))
                .findFirst()
                .orElseThrow(() -> new ConfigValidationException("No rule set supports setup template " + template.templateId()));
        validate(ruleSet, template, registry.roleMap());
    }

    private void validateTeamSizeRules(RuleSetDefinition ruleSet) {
        List<RoundTeamSizeRule> teamSizeRules = ruleSet.teamSizeRules();
        HashSet<Integer> rounds = new HashSet<>();
        for (RoundTeamSizeRule rule : teamSizeRules) {
            if (rule.round() <= 0) {
                throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " has invalid team round: " + rule.round());
            }
            if (rule.teamSize() <= 0) {
                throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " has invalid team size for round " + rule.round());
            }
            if (!rounds.add(rule.round())) {
                throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " has duplicate team rule for round " + rule.round());
            }
        }
    }

    private void validateMissionThresholds(RuleSetDefinition ruleSet) {
        Map<Integer, Integer> thresholds = ruleSet.failThresholdByRound();
        if (thresholds.isEmpty()) {
            throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " must define mission fail thresholds");
        }
        for (RoundTeamSizeRule rule : ruleSet.teamSizeRules()) {
            Integer threshold = thresholds.get(rule.round());
            if (threshold == null || threshold <= 0) {
                throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " missing valid fail threshold for round " + rule.round());
            }
        }
    }

    private void validateAssassinationRule(RuleSetDefinition ruleSet, AvalonConfigRegistry registry) {
        if (!ruleSet.assassinationRule().enabled()) {
            return;
        }
        if (ruleSet.assassinationRule().assassinRoleId().isBlank() || ruleSet.assassinationRule().merlinRoleId().isBlank()) {
            throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " has incomplete assassination rule");
        }
        if (!registry.roleMap().containsKey(ruleSet.assassinationRule().assassinRoleId())) {
            throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " references missing assassin role " + ruleSet.assassinationRule().assassinRoleId());
        }
        if (!registry.roleMap().containsKey(ruleSet.assassinationRule().merlinRoleId())) {
            throw new ConfigValidationException("Rule set " + ruleSet.ruleSetId() + " references missing merlin role " + ruleSet.assassinationRule().merlinRoleId());
        }
    }

    private void validateModelProfile(LlmModelDefinition modelProfile) {
        if (modelProfile.modelId() == null || modelProfile.modelId().isBlank()) {
            throw new ConfigValidationException("Model profile id must not be blank");
        }
        if (modelProfile.displayName() == null || modelProfile.displayName().isBlank()) {
            throw new ConfigValidationException("Model profile " + modelProfile.modelId() + " must define displayName");
        }
        if (modelProfile.provider() == null || modelProfile.provider().isBlank()) {
            throw new ConfigValidationException("Model profile " + modelProfile.modelId() + " must define provider");
        }
        if (modelProfile.modelName() == null || modelProfile.modelName().isBlank()) {
            throw new ConfigValidationException("Model profile " + modelProfile.modelId() + " must define modelName");
        }
        if (modelProfile.temperature() != null && modelProfile.temperature() < 0.0) {
            throw new ConfigValidationException("Model profile " + modelProfile.modelId() + " must define non-negative temperature");
        }
        if (modelProfile.maxTokens() != null && modelProfile.maxTokens() <= 0) {
            throw new ConfigValidationException("Model profile " + modelProfile.modelId() + " must define positive maxTokens");
        }
        validateModelProfileProviderOptions(modelProfile);
    }

    private void validateModelProfileProviderOptions(LlmModelDefinition modelProfile) {
        String apiKey = stringOption(modelProfile.providerOptions(), "apiKey");
        if (apiKey != null && !apiKey.isBlank()) {
            throw new ConfigValidationException(
                    "Model profile " + modelProfile.modelId() + " must not define providerOptions.apiKey in source-controlled config"
            );
        }
        String baseUrl = stringOption(modelProfile.providerOptions(), "baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.endsWith("/chat/completions")) {
            throw new ConfigValidationException(
                    "Model profile " + modelProfile.modelId() + " must define providerOptions.baseUrl as the API root, not the /chat/completions endpoint"
            );
        }
    }

    private String stringOption(Map<String, Object> options, String key) {
        if (options == null) {
            return null;
        }
        Object value = options.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
