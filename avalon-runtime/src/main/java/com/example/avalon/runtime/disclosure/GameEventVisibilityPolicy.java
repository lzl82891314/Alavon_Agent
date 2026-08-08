package com.example.avalon.runtime.disclosure;

import java.util.Set;

/** Explicit event disclosure registry. Unknown event types are private by default. */
public final class GameEventVisibilityPolicy {
    private static final Set<String> PUBLIC_EVENT_TYPES = Set.of(
            "GAME_CREATED",
            "GAME_STARTED",
            "PLAYER_ACTION",
            "TEAM_PROPOSED",
            "TEAM_VOTES_REVEALED",
            "TEAM_VOTE_REJECTED",
            "MISSION_RESULT_REVEALED",
            "MISSION_SUCCESS",
            "MISSION_FAILED",
            "ASSASSINATION_SUBMITTED",
            "GAME_PAUSED",
            "GAME_ENDED");

    private GameEventVisibilityPolicy() {
    }

    public static boolean isPublic(String eventType) {
        return eventType != null && PUBLIC_EVENT_TYPES.contains(eventType);
    }

    public static String visibility(String eventType) {
        return isPublic(eventType) ? "PUBLIC" : "PRIVATE";
    }
}
