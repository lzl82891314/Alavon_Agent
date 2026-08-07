package com.example.avalon.runtime.disclosure;

import com.example.avalon.persistence.model.GameEventRecord;

import java.util.Optional;

/** The single projection boundary for unauthenticated/public game event output. */
public interface DisclosurePolicy {
    Optional<GameEventRecord> publicEvent(GameEventRecord event);
}
