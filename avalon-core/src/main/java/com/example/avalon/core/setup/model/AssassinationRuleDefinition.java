package com.example.avalon.core.setup.model;

public record AssassinationRuleDefinition(
        boolean enabled,
        String assassinRoleId,
        String merlinRoleId
) {
}

