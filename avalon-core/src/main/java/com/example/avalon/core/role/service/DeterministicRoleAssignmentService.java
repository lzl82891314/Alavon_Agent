package com.example.avalon.core.role.service;

import com.example.avalon.core.common.exception.RoleAssignmentException;
import com.example.avalon.core.game.model.GamePlayer;
import com.example.avalon.core.game.model.GameSession;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.core.role.model.RoleDefinition;
import com.example.avalon.core.setup.model.RuleSetDefinition;
import com.example.avalon.core.setup.model.SetupTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class DeterministicRoleAssignmentService implements RoleAssignmentService {
    private final RoleKnowledgeResolver knowledgeResolver = new RoleKnowledgeResolver();

    @Override
    public List<RoleAssignment> assignRoles(
            GameSession session,
            RuleSetDefinition ruleSet,
            SetupTemplate setupTemplate,
            List<GamePlayer> players,
            Collection<RoleDefinition> roleDefinitions,
            long seed
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(ruleSet, "ruleSet");
        Objects.requireNonNull(setupTemplate, "setupTemplate");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(roleDefinitions, "roleDefinitions");

        if (players.size() != setupTemplate.playerCount()) {
            throw new RoleAssignmentException("Player count does not match setup template");
        }
        if (setupTemplate.roleIds().size() != players.size()) {
            throw new RoleAssignmentException("Role count does not match player count");
        }

        Map<String, RoleDefinition> roleDefinitionMap = new LinkedHashMap<>();
        for (RoleDefinition roleDefinition : roleDefinitions) {
            roleDefinitionMap.put(roleDefinition.roleId(), roleDefinition);
        }

        List<String> shuffledRoleIds = new ArrayList<>(setupTemplate.roleIds());
        java.util.Collections.shuffle(shuffledRoleIds, new Random(seed));

        List<GamePlayer> orderedPlayers = players.stream()
                .sorted(Comparator.comparingInt(GamePlayer::seatNo))
                .toList();

        List<RoleAssignment> assignments = new ArrayList<>();
        for (int index = 0; index < orderedPlayers.size(); index++) {
            GamePlayer player = orderedPlayers.get(index);
            String roleId = shuffledRoleIds.get(index);
            RoleDefinition roleDefinition = roleDefinitionMap.get(roleId);
            if (roleDefinition == null) {
                throw new RoleAssignmentException("Missing role definition for roleId: " + roleId);
            }
            assignments.add(new RoleAssignment(
                    session.gameId(),
                    player.playerId(),
                    player.seatNo(),
                    roleId,
                    roleDefinition.camp(),
                    null,
                    Instant.now()
            ));
        }

        List<RoleAssignment> finalAssignments = new ArrayList<>();
        for (RoleAssignment assignment : assignments) {
            RoleDefinition roleDefinition = roleDefinitionMap.get(assignment.roleId());
            var privateKnowledge = knowledgeResolver.resolve(
                    assignment.playerId(),
                    orderedPlayers,
                    assignments,
                    roleDefinitionMap,
                    roleDefinition
            );
            finalAssignments.add(new RoleAssignment(
                    assignment.gameId(),
                    assignment.playerId(),
                    assignment.seatNo(),
                    assignment.roleId(),
                    assignment.camp(),
                    privateKnowledge,
                    assignment.assignedAt()
            ));
        }

        return List.copyOf(finalAssignments);
    }
}
