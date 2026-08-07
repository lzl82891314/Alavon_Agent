package com.example.avalon.runtime.coordination;

import com.example.avalon.runtime.model.GameRuntimeState;

public record AdvanceResult(GameRuntimeState state, NextRequirement requirement, ActionBatch batch, boolean progressed, String message) {
    public boolean blocked() {
        return requirement instanceof ExternalPlayerActionRequirement
                || (batch != null && !batch.isComplete());
    }
}
