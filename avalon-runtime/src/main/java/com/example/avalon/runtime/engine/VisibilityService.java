package com.example.avalon.runtime.engine;

import com.example.avalon.core.player.memory.PlayerPrivateView;
import com.example.avalon.core.role.model.RoleAssignment;
import com.example.avalon.runtime.model.GameRuntimeState;
import com.example.avalon.runtime.service.RuntimeCoreContextFactory;

public class VisibilityService {
    private final com.example.avalon.core.role.service.VisibilityService delegate;
    private final RuntimeCoreContextFactory contextFactory;

    public VisibilityService() {
        this(new com.example.avalon.core.role.service.DefaultVisibilityService(), new RuntimeCoreContextFactory());
    }

    VisibilityService(com.example.avalon.core.role.service.VisibilityService delegate,
                      RuntimeCoreContextFactory contextFactory) {
        this.delegate = delegate;
        this.contextFactory = contextFactory;
    }

    public PlayerPrivateView buildPrivateView(GameRuntimeState state, RoleAssignment assignment) {
        return delegate.buildPrivateView(contextFactory.toRuleContext(state), assignment.playerId());
    }
}
