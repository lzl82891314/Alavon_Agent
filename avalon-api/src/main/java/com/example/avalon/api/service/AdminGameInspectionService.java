package com.example.avalon.api.service;

import com.example.avalon.api.dto.GameAuditEntryResponse;
import com.example.avalon.api.dto.GameEventEntryResponse;
import com.example.avalon.api.dto.PlayerPrivateViewResponse;

import java.util.List;

public interface AdminGameInspectionService {
    List<GameEventEntryResponse> getEvents(String gameId, AdminInspectionCapability capability);
    List<GameAuditEntryResponse> getAudit(String gameId, AdminInspectionCapability capability);

    PlayerPrivateViewResponse getPlayerView(String gameId, String playerId,
                                            AdminInspectionCapability capability);
}
