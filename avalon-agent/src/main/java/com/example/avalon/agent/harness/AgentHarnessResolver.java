package com.example.avalon.agent.harness;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/** Resolves the harness frozen into a player's agent configuration. */
public final class AgentHarnessResolver {
    private final Map<AgentHarnessType, AgentHarness> harnesses;

    public AgentHarnessResolver(Collection<AgentHarness> harnesses) {
        EnumMap<AgentHarnessType, AgentHarness> resolved = new EnumMap<>(AgentHarnessType.class);
        if (harnesses != null) {
            for (AgentHarness harness : harnesses) {
                AgentHarness previous = resolved.putIfAbsent(harness.type(), harness);
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate agent harness type: " + harness.type());
                }
            }
        }
        this.harnesses = Map.copyOf(resolved);
    }

    public AgentHarness resolve(AgentHarnessType type) {
        AgentHarnessType selected = type == null ? AgentHarnessType.TOOL_CALLING : type;
        AgentHarness harness = harnesses.get(selected);
        if (harness == null) {
            throw new IllegalStateException("Agent harness is not available: " + selected);
        }
        return harness;
    }
}
