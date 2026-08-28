package com.example.avalon.agent.tool;

public interface AgentTool {
    ToolDescriptor descriptor();

    ToolResult execute(ToolExecutionContext context, ToolCall call);
}
