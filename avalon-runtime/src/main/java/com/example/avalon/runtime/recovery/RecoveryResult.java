package com.example.avalon.runtime.recovery;

import com.example.avalon.persistence.model.GameEventRecord;
import com.example.avalon.persistence.model.GameSnapshotRecord;
import com.example.avalon.persistence.model.PlayerMemorySnapshotRecord;
import com.example.avalon.runtime.model.GameRuntimeState;

import java.util.List;

public record RecoveryResult(
        GameRuntimeState state,
        GameSnapshotRecord snapshot,
        List<GameEventRecord> eventsAfterSnapshot,
        List<PlayerMemorySnapshotRecord> restoredMemorySnapshots
) {
}

