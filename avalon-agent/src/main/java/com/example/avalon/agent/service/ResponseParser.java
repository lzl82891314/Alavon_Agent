package com.example.avalon.agent.service;

import com.example.avalon.agent.model.AgentTurnResult;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.PlayerActionType;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.game.model.AssassinationAction;
import com.example.avalon.core.game.model.MissionAction;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.PublicSpeechAction;
import com.example.avalon.core.game.model.TeamProposalAction;
import com.example.avalon.core.game.model.TeamVoteAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ResponseParser {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public PlayerAction parse(PlayerTurnContext context, AgentTurnResult turnResult) {
        Set<PlayerActionType> allowedActions = context.allowedActions().allowedActionTypes();
        if (allowedActions.isEmpty()) {
            throw new IllegalStateException("No allowed action available for player " + context.playerId());
        }
        if (allowedActions.contains(PlayerActionType.PUBLIC_SPEECH)) {
            String speech = turnResult.getPublicSpeech();
            if ((speech == null || speech.isBlank()) && hasActionJson(turnResult)) {
                JsonNode root = readAction(turnResult.getActionJson());
                speech = root.path("speechText").asText("");
            }
            if (speech == null || speech.isBlank()) {
                throw new IllegalStateException("Missing public speech content");
            }
            return new PublicSpeechAction(speech);
        }

        JsonNode root = readAction(turnResult.getActionJson());
        PlayerActionType actionType = parseActionType(root.path("actionType").asText(""));
        if (!allowedActions.contains(actionType)) {
            throw new IllegalStateException("Returned action type " + actionType + " is not allowed for " + context.playerId());
        }

        return switch (actionType) {
            case TEAM_PROPOSAL -> parseProposal(context, root);
            case TEAM_VOTE -> parseVote(root);
            case MISSION_ACTION -> parseMission(context, root);
            case ASSASSINATION -> parseAssassination(root);
            case PUBLIC_SPEECH -> new PublicSpeechAction(root.path("speechText").asText(""));
        };
    }

    private TeamProposalAction parseProposal(PlayerTurnContext context, JsonNode root) {
        List<String> selectedPlayerIds = new ArrayList<>();
        root.path("selectedPlayerIds").forEach(node -> selectedPlayerIds.add(node.asText()));
        if (selectedPlayerIds.size() != context.ruleSetDefinition().teamSizeForRound(context.roundNo())) {
            throw new IllegalStateException("Team proposal size does not match round rule");
        }
        return new TeamProposalAction(selectedPlayerIds);
    }

    private TeamVoteAction parseVote(JsonNode root) {
        return new TeamVoteAction(VoteChoice.valueOf(root.path("vote").asText("")));
    }

    private MissionAction parseMission(PlayerTurnContext context, JsonNode root) {
        MissionChoice choice = MissionChoice.valueOf(root.path("choice").asText(""));
        if (choice == MissionChoice.FAIL && context.privateView().camp() == com.example.avalon.core.game.enums.Camp.GOOD) {
            throw new IllegalStateException("Good players may not submit FAIL mission actions");
        }
        return new MissionAction(choice);
    }

    private AssassinationAction parseAssassination(JsonNode root) {
        String targetPlayerId = root.path("targetPlayerId").asText("");
        if (targetPlayerId.isBlank()) {
            throw new IllegalStateException("Missing assassination target");
        }
        return new AssassinationAction(targetPlayerId);
    }

    private JsonNode readAction(String actionJson) {
        if (actionJson == null || actionJson.isBlank()) {
            throw new IllegalStateException("Missing action JSON");
        }
        try {
            return objectMapper.readTree(actionJson);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse action JSON", e);
        }
    }

    private boolean hasActionJson(AgentTurnResult turnResult) {
        return turnResult.getActionJson() != null && !turnResult.getActionJson().isBlank();
    }

    private PlayerActionType parseActionType(String rawActionType) {
        try {
            return PlayerActionType.valueOf(rawActionType);
        } catch (Exception exception) {
            throw new IllegalStateException("Unsupported or missing action type: " + rawActionType, exception);
        }
    }
}
