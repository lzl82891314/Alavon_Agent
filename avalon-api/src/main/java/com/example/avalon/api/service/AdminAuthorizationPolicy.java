package com.example.avalon.api.service;

import org.springframework.stereotype.Component;

@Component
public final class AdminAuthorizationPolicy {
    private final LocalConsoleAdminAccess localConsoleAdminAccess;

    public AdminAuthorizationPolicy(LocalConsoleAdminAccess localConsoleAdminAccess) {
        this.localConsoleAdminAccess = localConsoleAdminAccess;
    }

    public void requireAuthorized(AdminInspectionCapability capability) {
        if (!localConsoleAdminAccess.authorizes(capability)) {
            throw new SecurityException("Administrative game inspection is not authorized");
        }
    }
}
