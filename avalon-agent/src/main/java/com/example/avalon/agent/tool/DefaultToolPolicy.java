package com.example.avalon.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public final class DefaultToolPolicy implements ToolPolicy {
    private static final Set<String> BASE_TOOLS = Set.of(
            "get_public_timeline", "list_legal_actions", "get_my_memory");
    private final ToolRegistry registry;

    public DefaultToolPolicy(ToolRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<ToolDescriptor> allowedTools(ToolExecutionContext context) {
        Set<String> allowed = new LinkedHashSet<>(BASE_TOOLS);
        if (hasPublicEvidence(context)) {
            allowed.add("get_vote_history");
            allowed.add("compare_player_consistency");
        }
        if (supportsTeamReasoning(context)) allowed.add("evaluate_team_combinations");
        List<String> configured = StrategicToolSupport.strings(
                context.request().getProviderOptions().get("toolAllowlist"));
        if (!configured.isEmpty()) allowed.retainAll(configured);
        return registry.descriptors().stream().filter(descriptor -> allowed.contains(descriptor.name())).toList();
    }

    @Override
    public boolean allows(ToolExecutionContext context, String toolName) {
        return allowedTools(context).stream().anyMatch(descriptor -> descriptor.name().equals(toolName));
    }

    private boolean hasPublicEvidence(ToolExecutionContext context) {
        return context.request().getObservationToSequence() > 0
                || !context.request().getObservationDelta().isEmpty();
    }

    private boolean supportsTeamReasoning(ToolExecutionContext context) {
        List<String> actions = context.request().getAllowedActions();
        return actions.contains("TEAM_PROPOSAL") || actions.contains("TEAM_VOTE")
                || actions.contains("PUBLIC_SPEECH");
    }
}
