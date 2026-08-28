package com.example.avalon.agent.tool;

import java.util.Collection;
import java.util.Optional;

public interface ToolRegistry {
    Optional<AgentTool> find(String toolName);

    Collection<ToolDescriptor> descriptors();
}
