package com.example.avalon.core.setup.model;

import java.util.List;

public record SetupTemplate(
        String templateId,
        Integer playerCount,
        boolean enabled,
        List<String> roleIds
) {
    public SetupTemplate {
        roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
    }
}

