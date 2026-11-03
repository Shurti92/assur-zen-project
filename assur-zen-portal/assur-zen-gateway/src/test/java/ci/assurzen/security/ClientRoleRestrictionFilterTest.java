package ci.assurzen.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Liste blanche du role CLIENT (compte mobile, 2026-11-02). */
class ClientRoleRestrictionFilterTest {

    @Test
    void autorise_authMobileI18nEtPointsTechniques() {
        assertTrue(ClientRoleRestrictionFilter.estAutorise("/api-assur-zen/auth/me"));
        assertTrue(ClientRoleRestrictionFilter.estAutorise("/api-assur-zen/auth/mobile/pin/verify"));
        assertTrue(ClientRoleRestrictionFilter.estAutorise("/api-assur-zen/mobile/contracts"));
        assertTrue(ClientRoleRestrictionFilter.estAutorise("/api-assur-zen/i18n/version"));
        assertTrue(ClientRoleRestrictionFilter.estAutorise("/actuator/health"));
        assertTrue(ClientRoleRestrictionFilter.estAutorise("/v3/api-docs"));
    }

    @Test
    void refuse_leBackOfficeEtLesEndpointsSeulementAuthentifies() {
        assertFalse(ClientRoleRestrictionFilter.estAutorise("/api-assur-zen/produits"));
        assertFalse(ClientRoleRestrictionFilter.estAutorise("/api-assur-zen/contracts"));
        assertFalse(ClientRoleRestrictionFilter.estAutorise("/api-assur-zen/users"));
        assertFalse(ClientRoleRestrictionFilter.estAutorise("/api-assur-zen/commercial-payroll"));
        assertFalse(ClientRoleRestrictionFilter.estAutorise("/api-assur-zen/authentication-bypass"));
        assertFalse(ClientRoleRestrictionFilter.estAutorise(null));
    }
}
