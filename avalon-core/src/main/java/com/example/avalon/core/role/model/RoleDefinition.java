package com.example.avalon.core.role.model;

import com.example.avalon.core.game.enums.Camp;

import java.util.List;

public record RoleDefinition(
        String roleId,
        String displayName,
        Camp camp,
        String description,
        List<KnowledgeRuleDefinition> knowledgeRules,
        List<String> actionCapabilities,
        boolean canLead,
        boolean canVote,
        boolean canJoinMission,
        boolean canAssassinate,
        List<String> passiveTraits
) {
    public RoleDefinition {
        knowledgeRules = knowledgeRules == null ? List.of() : List.copyOf(knowledgeRules);
        actionCapabilities = actionCapabilities == null ? List.of() : List.copyOf(actionCapabilities);
        passiveTraits = passiveTraits == null ? List.of() : List.copyOf(passiveTraits);
    }
}

