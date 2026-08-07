package com.example.avalon.api.dto;

import com.example.avalon.agent.model.PlayerAgentConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CreateGameRequest {
    private String ruleSetId;
    private String setupTemplateId;
    private Long seed;
    private List<PlayerSlotRequest> players = new ArrayList<>();
    private LlmSelectionRequest llmSelection;

    public String getRuleSetId() {
        return ruleSetId;
    }

    public void setRuleSetId(String ruleSetId) {
        this.ruleSetId = ruleSetId;
    }

    public String getSetupTemplateId() {
        return setupTemplateId;
    }

    public void setSetupTemplateId(String setupTemplateId) {
        this.setupTemplateId = setupTemplateId;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public List<PlayerSlotRequest> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerSlotRequest> players) {
        this.players = players == null ? new ArrayList<>() : new ArrayList<>(players);
    }

    public LlmSelectionRequest getLlmSelection() {
        return llmSelection;
    }

    public void setLlmSelection(LlmSelectionRequest llmSelection) {
        this.llmSelection = llmSelection;
    }

    public static class PlayerSlotRequest {
        private Integer seatNo;
        private String displayName;
        private String controllerType;
        private PlayerAgentConfig agentConfig;

        public Integer getSeatNo() {
            return seatNo;
        }

        public void setSeatNo(Integer seatNo) {
            this.seatNo = seatNo;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getControllerType() {
            return controllerType;
        }

        public void setControllerType(String controllerType) {
            this.controllerType = controllerType;
        }

        public PlayerAgentConfig getAgentConfig() {
            return agentConfig;
        }

        public void setAgentConfig(PlayerAgentConfig agentConfig) {
            this.agentConfig = agentConfig;
        }
    }

    public static class LlmSelectionRequest {
        private String mode;
        private Map<Integer, String> seatBindings = new LinkedHashMap<>();
        private Map<String, String> roleBindings = new LinkedHashMap<>();
        private List<String> candidateModelIds = new ArrayList<>();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Map<Integer, String> getSeatBindings() {
            return seatBindings;
        }

        public void setSeatBindings(Map<Integer, String> seatBindings) {
            this.seatBindings = seatBindings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(seatBindings);
        }

        public Map<String, String> getRoleBindings() {
            return roleBindings;
        }

        public void setRoleBindings(Map<String, String> roleBindings) {
            this.roleBindings = roleBindings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(roleBindings);
        }

        public List<String> getCandidateModelIds() {
            return candidateModelIds;
        }

        public void setCandidateModelIds(List<String> candidateModelIds) {
            this.candidateModelIds = candidateModelIds == null ? new ArrayList<>() : new ArrayList<>(candidateModelIds);
        }
    }
}
