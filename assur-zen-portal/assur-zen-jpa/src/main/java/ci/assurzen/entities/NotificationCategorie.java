package ci.assurzen.entities;

/**
 * Categories de notification reglables par l'utilisateur (Parametres > Notifications de l'Espace
 * Partenaire, 2026-10-03). Seules les categories reellement emises existent.
 */
public enum NotificationCategorie {
    /** Partenaire : commission gagnee (contrat signe) et rappel des commissions disponibles. */
    COMMISSION_GAGNEE,
    /** Argent verse : commission versee, demande de paiement payee ou en echec, commission commerciale payee.
     * Le SMS de confirmation de paiement reste toujours envoye. */
    COMMISSION_VERSEE,
    /** Commercial interne : bonus mensuel valide ou paye. */
    BONUS,
    /** Partenaire : echeance de contrat proche (2026-10-04). */
    ECHEANCE,
    /** Partenaire : prime non reversee a la compagnie (2026-10-04). */
    PRIME_A_REVERSER,
    /** Partenaire : document expirant, expire ou refuse (2026-10-04). */
    DOCUMENT,
    /** Nouvelle connexion depuis un appareil inconnu (2026-10-04) : push non desactivable. */
    SECURITE;

    public static NotificationCategorie of(PartenaireNotificationType type) {
        return switch (type) {
            case COMMISSION_GAGNEE, COMMISSION_DISPONIBLE -> COMMISSION_GAGNEE;
            case COMMISSION_VERSEE, DEMANDE_PAIEMENT_PAYEE, DEMANDE_PAIEMENT_ECHEC, COMMISSION_COMMERCIALE_PAYEE -> COMMISSION_VERSEE;
            case BONUS_VALIDE, BONUS_PAYE -> BONUS;
            case ECHEANCE_PROCHE -> ECHEANCE;
            case PRIME_NON_REVERSEE -> PRIME_A_REVERSER;
            case DOCUMENT_EXPIRE_BIENTOT, DOCUMENT_EXPIRE, DOCUMENT_REFUSE -> DOCUMENT;
            case NOUVELLE_CONNEXION -> SECURITE;
        };
    }

    /** Le SMS de cette categorie peut-il etre coupe par l'utilisateur ? Seulement les commissions gagnees. */
    public boolean smsModifiable() {
        return this == COMMISSION_GAGNEE;
    }

    /** Cette categorie n'envoie jamais de SMS (notification in-app et push seulement). */
    public boolean sansSms() {
        return this == ECHEANCE || this == PRIME_A_REVERSER || this == DOCUMENT || this == SECURITE;
    }

    /** Le push peut-il etre coupe ? Non pour la securite. */
    public boolean pushModifiable() {
        return this != SECURITE;
    }
}
