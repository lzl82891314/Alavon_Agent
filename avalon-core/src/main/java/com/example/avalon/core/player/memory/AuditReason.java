package com.example.avalon.core.player.memory;

import java.util.List;
import java.util.Map;

public record AuditReason(
        String goal,
        List<String> reasonSummary,
        Double confidence,
        Map<String, Object> beliefs
) {
    public AuditReason {
        reasonSummary = reasonSummary == null ? List.of() : List.copyOf(reasonSummary);
        beliefs = beliefs == null ? Map.of() : Map.copyOf(beliefs);
    }
}

