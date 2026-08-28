package com.example.avalon.agent.gateway;

public enum ModelStreamEventType {
    STARTED,
    REASONING_DELTA,
    CONTENT_DELTA,
    TOOL_CALL_ARGUMENT_DELTA,
    TOOL_CALL_COMPLETE,
    USAGE,
    COMPLETED,
    FAILED
}
