package com.example.avalon.core.role.service;

import com.example.avalon.core.game.model.GameRuleContext;
import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.core.role.model.RoleDefinition;

public class DefaultVisibilityService implements VisibilityService {
    private final RoleKnowledgeResolver knowledgeResolver = new RoleKnowledgeResolver();

    @Override
    public PlayerPrivateView buildPrivateView(GameRuleContext context, String playerId) {
        RoleAssignment assignment = context.roleAssignmentByPlayerId(playerId);
        RoleDefinition roleDefinition = context.roleDefinitionByRoleId(assignment.roleId());
        return new PlayerPrivateView(
                context.session().gameId(),
                playerId,
                assignment.seatNo(),
                roleDefinition.roleId(),
                roleDefinition.camp(),
                knowledgeResolver.resolve(
                        playerId,
                        context.players(),
                        context.roleAssignments(),
                        context.roleDefinitions(),
                        roleDefinition
                ),
                roleDefinition.actionCapabilities()
        );
    }
}
