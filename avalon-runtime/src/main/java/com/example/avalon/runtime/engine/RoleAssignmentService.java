package com.example.avalon.runtime.engine;

import com.example.avalon.core.game.model.GamePlayer;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.runtime.model.GameSetup;
import com.example.avalon.runtime.service.RuntimeCoreContextFactory;

import java.time.Instant;
import java.util.List;

public class RoleAssignmentService {
    private final com.example.avalon.core.role.service.RoleAssignmentService delegate;
    private final RuntimeCoreContextFactory contextFactory;

    public RoleAssignmentService() {
        this(new com.example.avalon.core.role.service.DeterministicRoleAssignmentService(), new RuntimeCoreContextFactory());
    }

    RoleAssignmentService(com.example.avalon.core.role.service.RoleAssignmentService delegate,
                          RuntimeCoreContextFactory contextFactory) {
        this.delegate = delegate;
        this.contextFactory = contextFactory;
    }

    public List<RoleAssignment> assignRoles(GameSetup setup) {
        List<GamePlayer> players = contextFactory.toGamePlayers(setup);
        return delegate.assignRoles(
                contextFactory.toWaitingSession(setup, Instant.now()),
                setup.ruleSetDefinition(),
                setup.setupTemplate(),
                players,
                setup.activeRoleDefinitions(),
                setup.seed()
        );
    }
}
