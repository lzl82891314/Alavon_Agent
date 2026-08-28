package com.example.avalon.agent.tool;

import java.time.Duration;

public interface ToolExecutor {
    ToolResult execute(ToolExecutionContext context, ToolCall call, Duration timeout);
}
