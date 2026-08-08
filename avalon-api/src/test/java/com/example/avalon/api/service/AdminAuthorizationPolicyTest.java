package com.example.avalon.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminAuthorizationPolicyTest {
    @Test
    void onlyCapabilityIssuedForLocalConsoleIsAccepted() {
        LocalConsoleAdminAccess access = new LocalConsoleAdminAccess();
        AdminAuthorizationPolicy policy = new AdminAuthorizationPolicy(access);

        assertDoesNotThrow(() -> policy.requireAuthorized(access.capability()));
        assertThrows(SecurityException.class, () -> policy.requireAuthorized(null));
        assertThrows(SecurityException.class,
                () -> policy.requireAuthorized(new AdminInspectionCapability()));
    }
}
