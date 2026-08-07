package com.example.avalon.runtime.persistence;

import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.model.GameRuntimeStateSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RuntimeStateCodec {
    private final ObjectMapper objectMapper;

    public RuntimeStateCodec() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    public RuntimeStateCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(GameRuntimeState state) {
        try {
            return objectMapper.writeValueAsString(GameRuntimeStateSnapshot.from(state));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize game runtime state", e);
        }
    }

    public GameRuntimeState deserialize(String json) {
        try {
            GameRuntimeStateSnapshot snapshot = objectMapper.readValue(json, GameRuntimeStateSnapshot.class);
            return GameRuntimeState.restore(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize game runtime state", e);
        }
    }
}

