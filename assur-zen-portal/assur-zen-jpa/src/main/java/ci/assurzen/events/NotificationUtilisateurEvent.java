package ci.assurzen.events;

import ci.assurzen.entities.PartenaireNotificationType;

/**
 * Demande de notification in-app (+ push si un appareil est abonne, jamais de SMS : les SMS existants de
 * ces evenements restent envoyes par leurs services d'origine) - 2026-10-03, centre de notifications
 * etendu au commercial interne et aux demandes de paiement.
 *
 * Destinataire : partenaireId (partenaire) OU userId (utilisateur interne, ex. commercial). Traitee APRES
 * commit dans sa propre transaction (NotificationUtilisateurListener) : elle ne peut jamais annuler ni
 * retarder le paiement ou la validation qui l'a produite.
 */
public record NotificationUtilisateurEvent(
        Long partenaireId,
        Long userId,
        PartenaireNotificationType type,
        String titre,
        String message,
        Long montant,
        String objetType,
        Long objetId
) {

    public static NotificationUtilisateurEvent pourPartenaire(Long partenaireId, PartenaireNotificationType type, String titre,
                                                              String message, Long montant, String objetType, Long objetId) {
        return new NotificationUtilisateurEvent(partenaireId, null, type, titre, message, montant, objetType, objetId);
    }

    public static NotificationUtilisateurEvent pourUtilisateur(Long userId, PartenaireNotificationType type, String titre,
                                                               String message, Long montant, String objetType, Long objetId) {
        return new NotificationUtilisateurEvent(null, userId, type, titre, message, montant, objetType, objetId);
    }
}
