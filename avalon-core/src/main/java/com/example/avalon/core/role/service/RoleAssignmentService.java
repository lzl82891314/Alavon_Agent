package com.example.avalon.core.role.service;

import com.example.avalon.core.game.model.GamePlayer;
import com.example.avalon.core.game.model.GameSession;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;

import java.util.Collection;
import java.util.List;

public interface RoleAssignmentService {
    List<RoleAssignment> assignRoles(
            GameSession session,
            RuleSetDefinition ruleSet,
            SetupTemplate setupTemplate,
            List<GamePlayer> players,
            Collection<RoleDefinition> roleDefinitions,
            long seed
    );
}

