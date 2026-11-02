package ci.assurzen.entities;

/** Type de notification in-app de l'espace partenaire (2026-09-23, paiement des commissions selon la
 * frequence du partenaire). */
public enum PartenaireNotificationType {
    /** Rappel periodique (HEBDOMADAIRE/QUINZAINE/MENSUELLE) : commissions disponibles a demander. */
    COMMISSION_DISPONIBLE,
    /** A_LA_DEMANDE : une nouvelle commission vient d'etre gagnee (contrat signe). */
    COMMISSION_GAGNEE,
    /** IMMEDIAT : la commission d'un contrat a ete versee automatiquement sur Wave. */
    COMMISSION_VERSEE,
    /** Demande de paiement (avance partenaire ou demande de commercial) payee - 2026-10-03. */
    DEMANDE_PAIEMENT_PAYEE,
    /** Virement SycaPay d'une demande de paiement en echec (relancable par l'administrateur) - 2026-10-03. */
    DEMANDE_PAIEMENT_ECHEC,
    /** Commercial interne : bonus mensuel valide (eligible) - 2026-10-03. */
    BONUS_VALIDE,
    /** Commercial interne : bonus mensuel paye - 2026-10-03. */
    BONUS_PAYE,
    /** Commercial interne : commission(s) commerciale(s) payee(s) - 2026-10-03. */
    COMMISSION_COMMERCIALE_PAYEE,
    /** Partenaire : echeance d'un contrat dans 30 ou 7 jours (rappel planifie) - 2026-10-04. */
    ECHEANCE_PROCHE,
    /** Partenaire : prime encaissee toujours pas reversee a la compagnie (rappel planifie). */
    PRIME_NON_REVERSEE,
    /** Partenaire : document expirant dans 30 ou 7 jours (rappel planifie). */
    DOCUMENT_EXPIRE_BIENTOT,
    /** Partenaire : document expire aujourd'hui (rappel planifie). */
    DOCUMENT_EXPIRE,
    /** Partenaire : document refuse par AssurZen (avec motif). */
    DOCUMENT_REFUSE,
    /** Connexion reussie depuis un appareil jamais vu sur ce compte (partenaire ou commercial). */
    NOUVELLE_CONNEXION
}
