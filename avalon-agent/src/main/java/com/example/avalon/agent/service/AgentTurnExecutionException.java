package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnRequest;
import com.example.avalon.agent.model.AgentTurnResult;

public class AgentTurnExecutionException extends RuntimeException {
    private final AgentTurnRequest request;
    private final AgentTurnResult lastTurnResult;
    private final int attempts;

    public AgentTurnExecutionException(String message,
                                       AgentTurnRequest request,
                                       AgentTurnResult lastTurnResult,
                                       int attempts,
                                       Throwable cause) {
        super(message, cause);
        this.request = request;
        this.lastTurnResult = lastTurnResult;
        this.attempts = attempts;
    }

    public AgentTurnRequest request() {
        return request;
    }

    public AgentTurnResult lastTurnResult() {
        return lastTurnResult;
    }

    public int attempts() {
        return attempts;
    }
}
