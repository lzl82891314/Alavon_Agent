package com.example.avalon.config.model;

import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AvalonConfigRegistry {
    private final Map<String, RuleSetDefinition> ruleSets;
    private final Map<String, RoleDefinition> roles;
    private final Map<String, SetupTemplate> setupTemplates;
    private final Map<String, LlmModelDefinition> modelProfiles;

    public AvalonConfigRegistry(
            Map<String, RuleSetDefinition> ruleSets,
            Map<String, RoleDefinition> roles,
            Map<String, SetupTemplate> setupTemplates,
            Map<String, LlmModelDefinition> modelProfiles
    ) {
        this.ruleSets = immutableMap(ruleSets);
        this.roles = immutableMap(roles);
        this.setupTemplates = immutableMap(setupTemplates);
        this.modelProfiles = immutableMap(modelProfiles);
    }

    public Optional<RuleSetDefinition> findRuleSet(String ruleSetId) {
        return Optional.ofNullable(ruleSets.get(ruleSetId));
    }

    public Optional<RoleDefinition> findRole(String roleId) {
        return Optional.ofNullable(roles.get(roleId));
    }

    public Optional<SetupTemplate> findSetupTemplate(String templateId) {
        return Optional.ofNullable(setupTemplates.get(templateId));
    }

    public Optional<LlmModelDefinition> findModelProfile(String modelId) {
        return Optional.ofNullable(modelProfiles.get(modelId));
    }

    public RuleSetDefinition requireRuleSet(String ruleSetId) {
        return findRuleSet(ruleSetId).orElseThrow(() -> new IllegalArgumentException("Unknown rule set: " + ruleSetId));
    }

    public RoleDefinition requireRole(String roleId) {
        return findRole(roleId).orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleId));
    }

    public SetupTemplate requireSetupTemplate(String templateId) {
        return findSetupTemplate(templateId).orElseThrow(() -> new IllegalArgumentException("Unknown setup template: " + templateId));
    }

    public LlmModelDefinition requireModelProfile(String modelId) {
        return findModelProfile(modelId).orElseThrow(() -> new IllegalArgumentException("Unknown model profile: " + modelId));
    }

    public Collection<RuleSetDefinition> ruleSets() {
        return List.copyOf(ruleSets.values());
    }

    public Collection<RoleDefinition> roles() {
        return List.copyOf(roles.values());
    }

    public Collection<SetupTemplate> setupTemplates() {
        return List.copyOf(setupTemplates.values());
    }

    public Collection<LlmModelDefinition> modelProfiles() {
        return List.copyOf(modelProfiles.values());
    }

    public Map<String, RuleSetDefinition> ruleSetMap() {
        return ruleSets;
    }

    public Map<String, RoleDefinition> roleMap() {
        return roles;
    }

    public Map<String, SetupTemplate> setupTemplateMap() {
        return setupTemplates;
    }

    public Map<String, LlmModelDefinition> modelProfileMap() {
        return modelProfiles;
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        Objects.requireNonNull(values, "values");
        return Map.copyOf(values);
    }
}
