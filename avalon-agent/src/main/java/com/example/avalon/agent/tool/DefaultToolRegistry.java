package com.example.avalon.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public final class DefaultToolRegistry implements ToolRegistry {
    private final Map<String, AgentTool> tools;

    public DefaultToolRegistry(List<AgentTool> tools) {
        Map<String, AgentTool> registered = new LinkedHashMap<>();
        for (AgentTool tool : tools) {
            AgentTool previous = registered.put(tool.descriptor().name(), tool);
            if (previous != null) throw new IllegalArgumentException("Duplicate tool: " + tool.descriptor().name());
        }
        this.tools = Map.copyOf(registered);
    }

    @Override
    public Optional<AgentTool> find(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    @Override
    public Collection<ToolDescriptor> descriptors() {
        return tools.values().stream().map(AgentTool::descriptor).toList();
    }
}
