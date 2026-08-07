package com.example.avalon.app.console;

import org.springframework.stereotype.Component;

@Component
public class ThreadSleepingConsolePlaybackDelayer implements ConsolePlaybackDelayer {
    @Override
    public void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("控制台播放等待被中断", exception);
        }
    }
}
