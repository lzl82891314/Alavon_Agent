package com.example.avalon.api.controller;

import com.example.avalon.api.dto.GameEventEntryResponse;
import com.example.avalon.api.service.GameApplicationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;

/** Public game event stream. Private views and audit entries are intentionally never sent here. */
@RestController
@RequestMapping("/games")
public class GameEventStreamController {
    private final GameApplicationService games;

    public GameEventStreamController(GameApplicationService games) { this.games = games; }

    @GetMapping(value = "/{gameId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String gameId, @RequestParam(name = "after", defaultValue = "0") long after) {
        SseEmitter emitter = new SseEmitter(0L);
        Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "avalon-sse-" + gameId);
            thread.setDaemon(true);
            return thread;
        }).execute(() -> publish(gameId, after, emitter));
        return emitter;
    }

    private void publish(String gameId, long after, SseEmitter emitter) {
        long cursor = Math.max(0, after);
        try {
            while (true) {
                List<GameEventEntryResponse> events = games.getEvents(gameId);
                for (GameEventEntryResponse event : events) {
                    if (event.getSeqNo() == null || event.getSeqNo() <= cursor || !isPublic(event)) continue;
                    emitter.send(SseEmitter.event().id(String.valueOf(event.getSeqNo())).name("game-event").data(event));
                    cursor = event.getSeqNo();
                }
                emitter.send(SseEmitter.event().name("heartbeat").data("ok"));
                Thread.sleep(1000L);
            }
        } catch (IOException | IllegalStateException ignored) {
            emitter.complete();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException failure) {
            emitter.completeWithError(failure);
        }
    }

    private boolean isPublic(GameEventEntryResponse event) {
        return event.getVisibility() == null || "PUBLIC".equalsIgnoreCase(event.getVisibility());
    }
}
