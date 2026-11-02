package ci.assurzen.services;

import ci.assurzen.security.AuthenticatedUserPrincipal;

import java.util.Locale;

/**
 * Petit utilitaire de roles partage entre les services du module Fidelisation (CampagneMarketing,
 * Fidelite, Satisfaction) et, depuis l'ajout de Segments/Templates, ce module egalement (roles
 * COMMUNICATION/READ_ONLY en plus) : plus de combinaisons de roles que le simple isAdmin()
 * duplique ailleurs (Sinistre/Contract), donc factorise ici plutot que reproduit a chaque fois.
 * Ne touche pas aux isAdmin() prives existants des autres services.
 */
final class FidelisationRoles {

    private FidelisationRoles() {
    }

    private static boolean hasRole(AuthenticatedUserPrincipal principal, String role) {
        return principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains(role);
    }

    static boolean isAdmin(AuthenticatedUserPrincipal principal) {
        return hasRole(principal, "ADMIN");
    }

    static boolean isMarketing(AuthenticatedUserPrincipal principal) {
        return hasRole(principal, "MARKETING");
    }

    static boolean isManager(AuthenticatedUserPrincipal principal) {
        return hasRole(principal, "MANAGER");
    }

    static boolean isCommunication(AuthenticatedUserPrincipal principal) {
        return hasRole(principal, "COMMUNICATION");
    }

    static boolean isSatisfactionManager(AuthenticatedUserPrincipal principal) {
        return hasRole(principal, "SATISFACTION_MANAGER");
    }

    static boolean isReclamationManager(AuthenticatedUserPrincipal principal) {
        return hasRole(principal, "RECLAMATION_MANAGER");
    }

    /** ADMIN, MARKETING et MANAGER voient l'ensemble du portefeuille (pas de scoping par salesRep). */
    static boolean canViewAll(AuthenticatedUserPrincipal principal) {
        return isAdmin(principal) || isMarketing(principal) || isManager(principal);
    }

    /** Segments/Templates : ecriture reservee a ADMIN/MARKETING/COMMUNICATION (READ_ONLY exclu). */
    static boolean canManageCommunication(AuthenticatedUserPrincipal principal) {
        return isAdmin(principal) || isMarketing(principal) || isCommunication(principal);
    }

    /** Imports Satisfaction : ecriture reservee a ADMIN/MARKETING/SATISFACTION_MANAGER (READ_ONLY exclu). */
    static boolean canManageSatisfactionImports(AuthenticatedUserPrincipal principal) {
        return isAdmin(principal) || isMarketing(principal) || isSatisfactionManager(principal);
    }

    /** Reclamations : ecriture (affectation/statut/cloture) reservee a ADMIN/MARKETING/MANAGER/RECLAMATION_MANAGER. */
    static boolean canManageReclamations(AuthenticatedUserPrincipal principal) {
        return isAdmin(principal) || isMarketing(principal) || isManager(principal) || isReclamationManager(principal);
    }
}
