package com.example.avalon.agent.tool;

import java.util.List;

public interface ToolPolicy {
    List<ToolDescriptor> allowedTools(ToolExecutionContext context);

    boolean allows(ToolExecutionContext context, String toolName);
}
