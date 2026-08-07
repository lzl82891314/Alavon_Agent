package com.example.avalon.core.role.service;

import com.example.avalon.core.common.exception.RoleAssignmentException;
import com.example.avalon.core.game.enums.Camp;
import com.example.avalon.core.game.model.GamePlayer;
import com.example.avalon.core.player.memory.PlayerPrivateKnowledge;
import com.example.avalon.core.player.memory.VisiblePlayerInfo;
import com.example.avalon.core.role.model.KnowledgeRuleDefinition;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.core.role.model.RoleDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class RoleKnowledgeResolver {
    PlayerPrivateKnowledge resolve(String observerPlayerId,
                                   List<GamePlayer> allPlayers,
                                   List<RoleAssignment> assignments,
                                   Map<String, RoleDefinition> roleDefinitionMap,
                                   RoleDefinition observerRoleDefinition) {
        List<VisiblePlayerInfo> visiblePlayers = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (KnowledgeRuleDefinition knowledgeRule : observerRoleDefinition.knowledgeRules()) {
            switch (knowledgeRule.type()) {
                case SEE_PLAYERS_BY_CAMP -> addCampVisibility(visiblePlayers, allPlayers, assignments, roleDefinitionMap, observerPlayerId, knowledgeRule);
                case SEE_PLAYERS_BY_ROLE -> addExactRoleVisibility(visiblePlayers, allPlayers, assignments, roleDefinitionMap, observerPlayerId, knowledgeRule);
                case SEE_ROLE_AMBIGUITY -> addRoleAmbiguityVisibility(visiblePlayers, allPlayers, assignments, roleDefinitionMap, observerPlayerId, knowledgeRule);
                case SEE_ALLIED_EVIL_PLAYERS -> addAlliedEvilVisibility(visiblePlayers, allPlayers, assignments, roleDefinitionMap, observerPlayerId, knowledgeRule);
            }
        }

        notes.add(defaultKnowledgeNote(observerRoleDefinition.roleId()));
        return new PlayerPrivateKnowledge(visiblePlayers, notes);
    }

    private void addCampVisibility(List<VisiblePlayerInfo> visiblePlayers,
                                   List<GamePlayer> allPlayers,
                                   List<RoleAssignment> assignments,
                                   Map<String, RoleDefinition> roleDefinitionMap,
                                   String observerPlayerId,
                                   KnowledgeRuleDefinition rule) {
        for (GamePlayer candidate : allPlayers) {
            if (Objects.equals(candidate.playerId(), observerPlayerId)) {
                continue;
            }
            RoleDefinition candidateRole = roleDefinitionByPlayer(assignments, roleDefinitionMap, candidate.playerId());
            if (candidateRole.camp() != rule.targetCamp() || rule.exclusions().contains(candidateRole.roleId())) {
                continue;
            }
            visiblePlayers.add(new VisiblePlayerInfo(
                    candidate.playerId(),
                    candidate.seatNo(),
                    candidate.displayName(),
                    candidateRole.roleId(),
                    candidateRole.camp(),
                    List.of(candidateRole.roleId())
            ));
        }
    }

    private void addExactRoleVisibility(List<VisiblePlayerInfo> visiblePlayers,
                                        List<GamePlayer> allPlayers,
                                        List<RoleAssignment> assignments,
                                        Map<String, RoleDefinition> roleDefinitionMap,
                                        String observerPlayerId,
                                        KnowledgeRuleDefinition rule) {
        for (GamePlayer candidate : allPlayers) {
            if (Objects.equals(candidate.playerId(), observerPlayerId)) {
                continue;
            }
            RoleDefinition candidateRole = roleDefinitionByPlayer(assignments, roleDefinitionMap, candidate.playerId());
            if (!rule.targetRoleIds().contains(candidateRole.roleId()) || rule.exclusions().contains(candidateRole.roleId())) {
                continue;
            }
            visiblePlayers.add(new VisiblePlayerInfo(
                    candidate.playerId(),
                    candidate.seatNo(),
                    candidate.displayName(),
                    candidateRole.roleId(),
                    candidateRole.camp(),
                    List.of(candidateRole.roleId())
            ));
        }
    }

    private void addRoleAmbiguityVisibility(List<VisiblePlayerInfo> visiblePlayers,
                                            List<GamePlayer> allPlayers,
                                            List<RoleAssignment> assignments,
                                            Map<String, RoleDefinition> roleDefinitionMap,
                                            String observerPlayerId,
                                            KnowledgeRuleDefinition rule) {
        for (GamePlayer candidate : allPlayers) {
            if (Objects.equals(candidate.playerId(), observerPlayerId)) {
                continue;
            }
            RoleDefinition candidateRole = roleDefinitionByPlayer(assignments, roleDefinitionMap, candidate.playerId());
            if (!rule.targetRoleIds().contains(candidateRole.roleId()) || rule.exclusions().contains(candidateRole.roleId())) {
                continue;
            }
            visiblePlayers.add(new VisiblePlayerInfo(
                    candidate.playerId(),
                    candidate.seatNo(),
                    candidate.displayName(),
                    null,
                    candidateRole.camp(),
                    rule.targetRoleIds()
            ));
        }
    }

    private void addAlliedEvilVisibility(List<VisiblePlayerInfo> visiblePlayers,
                                         List<GamePlayer> allPlayers,
                                         List<RoleAssignment> assignments,
                                         Map<String, RoleDefinition> roleDefinitionMap,
                                         String observerPlayerId,
                                         KnowledgeRuleDefinition rule) {
        for (GamePlayer candidate : allPlayers) {
            if (Objects.equals(candidate.playerId(), observerPlayerId)) {
                continue;
            }
            RoleDefinition candidateRole = roleDefinitionByPlayer(assignments, roleDefinitionMap, candidate.playerId());
            if (candidateRole.camp() != Camp.EVIL || rule.exclusions().contains(candidateRole.roleId())) {
                continue;
            }
            visiblePlayers.add(new VisiblePlayerInfo(
                    candidate.playerId(),
                    candidate.seatNo(),
                    candidate.displayName(),
                    candidateRole.roleId(),
                    candidateRole.camp(),
                    List.of(candidateRole.roleId())
            ));
        }
    }

    private RoleDefinition roleDefinitionByPlayer(List<RoleAssignment> assignments,
                                                  Map<String, RoleDefinition> roleDefinitionMap,
                                                  String playerId) {
        for (RoleAssignment assignment : assignments) {
            if (Objects.equals(assignment.playerId(), playerId)) {
                return roleDefinitionMap.get(assignment.roleId());
            }
        }
        throw new RoleAssignmentException("Role assignment missing for player: " + playerId);
    }

    private String defaultKnowledgeNote(String roleId) {
        return switch (roleId) {
            case "MERLIN" -> "You know evil players except excluded roles.";
            case "PERCIVAL" -> "You see Merlin and Morgana as candidates.";
            case "ASSASSIN" -> "You know allied evil players except excluded roles.";
            default -> "No special private knowledge.";
        };
    }
}
