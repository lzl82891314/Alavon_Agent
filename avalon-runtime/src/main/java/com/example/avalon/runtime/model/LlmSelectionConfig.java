package com.example.avalon.runtime.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LlmSelectionConfig(
        LlmSelectionMode mode,
        Map<Integer, String> seatBindings,
        Map<String, String> roleBindings,
        List<String> candidateModelIds
) {
    public LlmSelectionConfig {
        mode = mode == null ? LlmSelectionMode.NONE : mode;
        seatBindings = seatBindings == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(seatBindings));
        roleBindings = roleBindings == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(roleBindings));
        candidateModelIds = candidateModelIds == null ? List.of() : List.copyOf(candidateModelIds);
    }

    public static LlmSelectionConfig none() {
        return new LlmSelectionConfig(LlmSelectionMode.NONE, Map.of(), Map.of(), List.of());
    }
}
