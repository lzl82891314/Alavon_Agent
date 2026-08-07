package com.example.avalon.core.setup.service;

import com.example.avalon.core.common.exception.GameConfigurationException;
import com.example.avalon.core.game.model.GamePlayer;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DefaultSetupValidationService implements SetupValidationService {
    @Override
    public void validate(
            RuleSetDefinition ruleSet,
            SetupTemplate setupTemplate,
            List<GamePlayer> players,
            Collection<RoleDefinition> roleDefinitions
    ) {
        if (ruleSet == null) {
            throw new GameConfigurationException("ruleSet must not be null");
        }
        if (setupTemplate == null) {
            throw new GameConfigurationException("setupTemplate must not be null");
        }
        if (players == null || players.isEmpty()) {
            throw new GameConfigurationException("players must not be empty");
        }
        if (roleDefinitions == null || roleDefinitions.isEmpty()) {
            throw new GameConfigurationException("roleDefinitions must not be empty");
        }
        if (!setupTemplate.enabled()) {
            throw new GameConfigurationException("setupTemplate is disabled: " + setupTemplate.templateId());
        }
        if (!ruleSet.supportsSetupTemplate(setupTemplate.templateId())) {
            throw new GameConfigurationException("ruleSet does not support setup template: " + setupTemplate.templateId());
        }
        if (!Objects.equals(setupTemplate.playerCount(), players.size())) {
            throw new GameConfigurationException("player count does not match setup template");
        }
        if (players.size() < ruleSet.minPlayers() || players.size() > ruleSet.maxPlayers()) {
            throw new GameConfigurationException("player count outside rule set bounds");
        }
        if (setupTemplate.roleIds().size() != setupTemplate.playerCount()) {
            throw new GameConfigurationException("role count does not match player count");
        }

        Map<String, RoleDefinition> roleMap = roleDefinitions.stream().collect(java.util.stream.Collectors.toMap(RoleDefinition::roleId, role -> role));
        for (String roleId : setupTemplate.roleIds()) {
            if (!roleMap.containsKey(roleId)) {
                throw new GameConfigurationException("missing role definition for roleId: " + roleId);
            }
        }
        if (ruleSet.teamSizeRules().isEmpty()) {
            throw new GameConfigurationException("team size rules must not be empty");
        }
        for (int round = 1; round <= 5; round++) {
            ruleSet.teamSizeForRound(round);
            ruleSet.failThresholdForRound(round);
        }
    }
}
