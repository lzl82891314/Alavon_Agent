package com.example.avalon.api.service;

import com.example.avalon.api.dto.CreateGameRequest;
import com.example.avalon.api.dto.GameAuditEntryResponse;
import com.example.avalon.api.dto.GameActionSubmissionRequest;
import com.example.avalon.api.dto.GameEventEntryResponse;
import com.example.avalon.api.dto.GameStateResponse;
import com.example.avalon.api.dto.GameSummaryResponse;
import com.example.avalon.api.dto.PlayerPrivateViewResponse;

import java.util.List;

public interface GameApplicationService {
    GameSummaryResponse createGame(CreateGameRequest request);

    GameSummaryResponse startGame(String gameId);

    GameSummaryResponse stepGame(String gameId);

    GameSummaryResponse runGame(String gameId);

    GameStateResponse getState(String gameId);

    List<GameEventEntryResponse> getEvents(String gameId);

    List<GameEventEntryResponse> getReplay(String gameId);

    List<GameAuditEntryResponse> getAudit(String gameId);

    PlayerPrivateViewResponse getPlayerView(String gameId, String playerId);

    GameSummaryResponse submitPlayerAction(String gameId, String playerId, GameActionSubmissionRequest request);
}
