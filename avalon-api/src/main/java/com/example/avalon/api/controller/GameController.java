package com.example.avalon.api.controller;

import com.example.avalon.api.dto.CreateGameRequest;
import com.example.avalon.api.dto.GameAuditEntryResponse;
import com.example.avalon.api.dto.GameActionSubmissionRequest;
import com.example.avalon.api.dto.GameEventEntryResponse;
import com.example.avalon.api.dto.GameStateResponse;
import com.example.avalon.api.dto.GameSummaryResponse;
import com.example.avalon.api.dto.PlayerPrivateViewResponse;
import com.example.avalon.api.service.GameApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/games")
public class GameController {
    private final GameApplicationService gameApplicationService;

    public GameController(GameApplicationService gameApplicationService) {
        this.gameApplicationService = gameApplicationService;
    }

    @PostMapping
    public GameSummaryResponse createGame(@RequestBody CreateGameRequest request) {
        return gameApplicationService.createGame(request);
    }

    @PostMapping("/{gameId}/start")
    public GameSummaryResponse startGame(@PathVariable("gameId") String gameId) {
        return gameApplicationService.startGame(gameId);
    }

    @PostMapping("/{gameId}/step")
    public GameSummaryResponse stepGame(@PathVariable("gameId") String gameId) {
        return gameApplicationService.stepGame(gameId);
    }

    @PostMapping("/{gameId}/run")
    public GameSummaryResponse runGame(@PathVariable("gameId") String gameId) {
        return gameApplicationService.runGame(gameId);
    }

    @GetMapping("/{gameId}/state")
    public GameStateResponse getState(@PathVariable("gameId") String gameId) {
        return gameApplicationService.getState(gameId);
    }

    @GetMapping("/{gameId}/events")
    public List<GameEventEntryResponse> getEvents(@PathVariable("gameId") String gameId) {
        return gameApplicationService.getEvents(gameId);
    }

    @GetMapping("/{gameId}/replay")
    public List<GameEventEntryResponse> getReplay(@PathVariable("gameId") String gameId) {
        return gameApplicationService.getReplay(gameId);
    }

    @GetMapping("/{gameId}/audit")
    public List<GameAuditEntryResponse> getAudit(@PathVariable("gameId") String gameId) {
        return gameApplicationService.getAudit(gameId);
    }

    @GetMapping("/{gameId}/players/{playerId}/view")
    public PlayerPrivateViewResponse getPlayerView(@PathVariable("gameId") String gameId, @PathVariable("playerId") String playerId) {
        return gameApplicationService.getPlayerView(gameId, playerId);
    }

    @PostMapping("/{gameId}/players/{playerId}/actions")
    public GameSummaryResponse submitPlayerAction(@PathVariable("gameId") String gameId,
                                                  @PathVariable("playerId") String playerId,
                                                  @RequestBody GameActionSubmissionRequest request) {
        return gameApplicationService.submitPlayerAction(gameId, playerId, request);
    }
}
