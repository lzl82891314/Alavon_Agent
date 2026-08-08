package com.example.avalon.api.service;

import org.springframework.stereotype.Component;

/** Issues the single capability reserved for the trusted local console. */
@Component
public final class LocalConsoleAdminAccess {
    private final AdminInspectionCapability capability = new AdminInspectionCapability();

    public AdminInspectionCapability capability() {
        return capability;
    }

    boolean authorizes(AdminInspectionCapability candidate) {
        return candidate == capability;
    }
}
