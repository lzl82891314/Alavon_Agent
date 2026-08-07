package com.example.avalon.api.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlayerPrivateViewResponse {
    private Integer seatNo;
    private String roleSummary;
    private Map<String, Object> privateKnowledge = new LinkedHashMap<>();
    private Map<String, Object> memorySnapshot = new LinkedHashMap<>();
    private List<String> allowedActions = List.of();

    public Integer getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(Integer seatNo) {
        this.seatNo = seatNo;
    }

    public String getRoleSummary() {
        return roleSummary;
    }

    public void setRoleSummary(String roleSummary) {
        this.roleSummary = roleSummary;
    }

    public Map<String, Object> getPrivateKnowledge() {
        return privateKnowledge;
    }

    public void setPrivateKnowledge(Map<String, Object> privateKnowledge) {
        this.privateKnowledge = privateKnowledge == null ? new LinkedHashMap<>() : new LinkedHashMap<>(privateKnowledge);
    }

    public Map<String, Object> getMemorySnapshot() {
        return memorySnapshot;
    }

    public void setMemorySnapshot(Map<String, Object> memorySnapshot) {
        this.memorySnapshot = memorySnapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(memorySnapshot);
    }

    public List<String> getAllowedActions() {
        return allowedActions;
    }

    public void setAllowedActions(List<String> allowedActions) {
        this.allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }
}

