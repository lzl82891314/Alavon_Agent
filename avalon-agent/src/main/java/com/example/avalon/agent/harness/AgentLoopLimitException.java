package com.example.avalon.agent.harness;

public final class AgentLoopLimitException extends RuntimeException {
    private final String failureKind;

    public AgentLoopLimitException(String failureKind, String message) {
        super(message);
        this.failureKind = failureKind;
    }

    public String failureKind() {
        return failureKind;
    }
}
