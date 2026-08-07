package com.example.avalon.core.game.model;

import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record GameRuleContext(
        GameSession session,
        List<GamePlayer> players,
        List<RoleAssignment> roleAssignments,
        RuleSetDefinition ruleSet,
        SetupTemplate setupTemplate,
        Map<String, RoleDefinition> roleDefinitions
) {
    public GameRuleContext {
        players = players == null ? List.of() : List.copyOf(players);
        roleAssignments = roleAssignments == null ? List.of() : List.copyOf(roleAssignments);
        roleDefinitions = roleDefinitions == null ? Map.of() : Map.copyOf(roleDefinitions);
    }

    public GamePlayer playerById(String playerId) {
        return players.stream()
                .filter(player -> Objects.equals(player.playerId(), playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown player: " + playerId));
    }

    public GamePlayer playerBySeatNo(Integer seatNo) {
        return players.stream()
                .filter(player -> Objects.equals(player.seatNo(), seatNo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown seat: " + seatNo));
    }

    public RoleAssignment roleAssignmentByPlayerId(String playerId) {
        return roleAssignments.stream()
                .filter(assignment -> Objects.equals(assignment.playerId(), playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role assignment for player: " + playerId));
    }

    public RoleDefinition roleDefinitionByPlayerId(String playerId) {
        RoleDefinition definition = roleDefinitions.get(roleAssignmentByPlayerId(playerId).roleId());
        if (definition == null) {
            throw new IllegalArgumentException("Unknown role definition for player: " + playerId);
        }
        return definition;
    }

    public RoleDefinition roleDefinitionByRoleId(String roleId) {
        RoleDefinition definition = roleDefinitions.get(roleId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown role definition: " + roleId);
        }
        return definition;
    }

    public List<GamePlayer> currentTeamPlayers() {
        return roleAssignments.stream()
                .filter(assignment -> session.currentTeamPlayerIds().contains(assignment.playerId()))
                .map(assignment -> playerById(assignment.playerId()))
                .toList();
    }

    public Integer playerCount() {
        return players.size();
    }
}
