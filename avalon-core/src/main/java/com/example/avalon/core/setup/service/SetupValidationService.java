package com.example.avalon.core.setup.service;

import com.example.avalon.core.game.model.GamePlayer;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;

import java.util.Collection;
import java.util.List;

public interface SetupValidationService {
    void validate(
            RuleSetDefinition ruleSet,
            SetupTemplate setupTemplate,
            List<GamePlayer> players,
            Collection<RoleDefinition> roleDefinitions
    );
}

