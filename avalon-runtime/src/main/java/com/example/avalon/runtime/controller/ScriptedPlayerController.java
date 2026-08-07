package com.example.avalon.runtime.controller;

import com.example.avalon.core.game.enums.GamePhase;
import com.example.avalon.core.game.enums.MissionChoice;
import com.example.avalon.core.game.enums.VoteChoice;
import com.example.avalon.core.game.model.AssassinationAction;
import com.example.avalon.core.game.model.MissionAction;
import com.example.avalon.core.game.model.PlayerAction;
import com.example.avalon.core.game.model.PlayerActionResult;
import com.example.avalon.core.game.model.PlayerTurnContext;
import com.example.avalon.core.game.model.PublicSpeechAction;
import com.example.avalon.core.game.model.PublicPlayerSummary;
import com.example.avalon.core.game.model.TeamProposalAction;
import com.example.avalon.core.game.model.TeamVoteAction;
import com.example.avalon.core.player.controller.PlayerController;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.player.memory.VisiblePlayerInfo;

import java.util.ArrayList;
import java.util.List;

public class ScriptedPlayerController implements PlayerController {
    @Override
    public PlayerActionResult act(PlayerTurnContext context) {
        GamePhase phase = GamePhase.valueOf(context.phase());
        PlayerAction action = switch (phase) {
            case DISCUSSION -> new PublicSpeechAction(buildSpeech(context));
            case TEAM_PROPOSAL -> new TeamProposalAction(buildProposal(context));
            case TEAM_VOTE -> new TeamVoteAction(VoteChoice.APPROVE);
            case MISSION_ACTION -> new MissionAction(
                    context.privateView().camp().name().equals("EVIL") ? MissionChoice.FAIL : MissionChoice.SUCCESS);
            case ASSASSINATION -> new AssassinationAction(findMerlinPlayerId(context));
            default -> throw new IllegalStateException("Scripted controller cannot act in phase " + phase);
        };
        String speech = action instanceof PublicSpeechAction publicSpeechAction ? publicSpeechAction.speechText() : null;
        return new PlayerActionResult(speech, action, null, null, java.util.Map.of());
    }

    private String buildSpeech(PlayerTurnContext context) {
        return "Seat " + context.seatNo() + " confirms the current board state in round " + context.roundNo() + ".";
    }

    private List<String> buildProposal(PlayerTurnContext context) {
        List<String> proposal = new ArrayList<>();
        proposal.add(context.playerId());
        int seat = context.seatNo();
        int teamSize = context.ruleSetDefinition().teamSizeForRound(context.roundNo());
        while (proposal.size() < teamSize) {
            seat = seatAtOffset(context, seat, 1);
            String playerId = playerIdForSeat(context, seat);
            if (playerId != null && !proposal.contains(playerId)) {
                proposal.add(playerId);
            }
        }
        return proposal;
    }

    private int seatAtOffset(PlayerTurnContext context, int seatNo, int offset) {
        int playerCount = Math.max(1, context.publicState().players().size());
        int index = (seatNo - 1 + offset) % playerCount;
        if (index < 0) {
            index += playerCount;
        }
        return index + 1;
    }

    private String playerIdForSeat(PlayerTurnContext context, int seatNo) {
        for (PublicPlayerSummary player : context.publicState().players()) {
            if (player.seatNo().equals(seatNo)) {
                return player.playerId();
            }
        }
        return null;
    }

    private String findMerlinPlayerId(PlayerTurnContext context) {
        PlayerPrivateView privateView = context.privateView();
        PlayerPrivateKnowledge knowledge = privateView.knowledge();
        for (VisiblePlayerInfo visiblePlayer : knowledge.visiblePlayers()) {
            if ("MERLIN".equals(visiblePlayer.exactRoleId())) {
                return visiblePlayer.playerId();
            }
        }
        for (String clue : knowledge.notes()) {
            if (clue.startsWith("MERLIN_PLAYER=")) {
                return clue.substring("MERLIN_PLAYER=".length());
            }
        }
        return context.playerId();
    }
}
