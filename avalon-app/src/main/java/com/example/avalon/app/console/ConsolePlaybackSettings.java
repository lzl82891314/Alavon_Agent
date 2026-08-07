package com.example.avalon.app.console;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConsolePlaybackSettings {
    private final boolean enabled;
    private final long actorLeadInMs;
    private final long afterStepMs;

    public ConsolePlaybackSettings(
            @Value("${avalon.console.playback.enabled:true}") boolean enabled,
            @Value("${avalon.console.playback.actor-lead-in-ms:1000}") long actorLeadInMs,
            @Value("${avalon.console.playback.after-step-ms:800}") long afterStepMs
    ) {
        this.enabled = enabled;
        this.actorLeadInMs = Math.max(0L, actorLeadInMs);
        this.afterStepMs = Math.max(0L, afterStepMs);
    }

    public boolean enabled() {
        return enabled;
    }

    public long actorLeadInMs() {
        return actorLeadInMs;
    }

    public long afterStepMs() {
        return afterStepMs;
    }
}
