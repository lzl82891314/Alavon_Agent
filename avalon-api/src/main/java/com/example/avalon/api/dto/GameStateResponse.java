package com.example.avalon.api.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class GameStateResponse {
    private String gameId;
    private String status;
    private String phase;
    private Integer roundNo;
    private Map<String, Object> publicState = new LinkedHashMap<>();
    private String nextRequiredActor;
    private String waitingReason;

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Map<String, Object> getPublicState() {
        return publicState;
    }

    public void setPublicState(Map<String, Object> publicState) {
        this.publicState = publicState == null ? new LinkedHashMap<>() : new LinkedHashMap<>(publicState);
    }

    public String getNextRequiredActor() {
        return nextRequiredActor;
    }

    public void setNextRequiredActor(String nextRequiredActor) {
        this.nextRequiredActor = nextRequiredActor;
    }

    public String getWaitingReason() {
        return waitingReason;
    }

    public void setWaitingReason(String waitingReason) {
        this.waitingReason = waitingReason;
    }
}

