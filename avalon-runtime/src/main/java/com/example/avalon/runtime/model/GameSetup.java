package com.example.avalon.runtime.model;

import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public record GameSetup(
        String gameId,
        String ruleSetId,
        RuleSetDefinition ruleSetDefinition,
        String setupTemplateId,
        SetupTemplate setupTemplate,
        long seed,
        Map<String, RoleDefinition> roleDefinitions,
        List<PlayerRegistration> players,
        LlmSelectionConfig llmSelectionConfig) {
    public GameSetup {
        roleDefinitions = roleDefinitions == null ? Map.of() : Map.copyOf(roleDefinitions);
        players = players == null ? List.of() : List.copyOf(players);
        llmSelectionConfig = llmSelectionConfig == null ? LlmSelectionConfig.none() : llmSelectionConfig;
    }

    public GameSetup(String gameId,
                     String ruleSetId,
                     RuleSetDefinition ruleSetDefinition,
                     String setupTemplateId,
                     SetupTemplate setupTemplate,
                     long seed,
                     Map<String, RoleDefinition> roleDefinitions,
                     List<PlayerRegistration> players) {
        this(gameId, ruleSetId, ruleSetDefinition, setupTemplateId, setupTemplate, seed, roleDefinitions, players, LlmSelectionConfig.none());
    }

    public RoleDefinition requireRoleDefinition(String roleId) {
        RoleDefinition definition = roleDefinitions.get(roleId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown role definition: " + roleId);
        }
        return definition;
    }

    public Collection<RoleDefinition> activeRoleDefinitions() {
        return setupTemplate.roleIds().stream()
                .map(this::requireRoleDefinition)
                .toList();
    }
}
