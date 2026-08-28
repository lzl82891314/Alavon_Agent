package com.example.avalon.agent.gateway;

public final class ModelToolCallingUnsupportedException extends RuntimeException {
    public ModelToolCallingUnsupportedException(String protocol) {
        super("Model protocol does not support tool calling: " + protocol);
    }
}
