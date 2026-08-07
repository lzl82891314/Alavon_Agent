package com.example.avalon.core.role.model;

import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.role.enums.KnowledgeRuleType;

import java.util.List;

public record KnowledgeRuleDefinition(
        KnowledgeRuleType type,
        Camp targetCamp,
        List<String> targetRoleIds,
        List<String> exclusions
) {
    public KnowledgeRuleDefinition {
        targetRoleIds = targetRoleIds == null ? List.of() : List.copyOf(targetRoleIds);
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }
}

