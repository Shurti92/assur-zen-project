package ci.assurzen.entities;

public enum TypeMessageSms {
    bienvenue,
    avis_echeance,
    fin_contrat,
    sinistre_declare,
    sinistre_suivi,
    otp_verification,
    relance_echeance_paiement,
    enquete_satisfaction,
    reclamation_creee,
    reclamation_suivi,
    bienvenue_partenaire,
    bienvenue_backoffice,
    bienvenue_commercial,
    renvoi_acces,
    renvoi_acces_partenaire,
    commission_payee,
    commission_demande_recue,
    prime_objectif_payee,
    pret_accorde,
    echeance_pret_rappel,
    echeance_pret_jour_j,
    bonus_commercial_paye,
    commission_commerciale_payee,
    vehicule_incorpore_flotte,
    commission_vignette_payee,
    /** Une seule valeur pour TOUS les changements de statut d'une DemandeVignetteMoto (10 statuts
     * possibles) - meme convention que reclamation_suivi ci-dessus (deja un seul type generique pour
     * plusieurs statuts d'une Reclamation) plutot qu'une valeur par statut : aucune regle metier ne
     * distingue ces messages entre eux (contrairement a otp_verification, verifie explicitement par
     * ProviderService pour bypasser SMS_ENABLED), seul le CODE du CommunicationTemplate varie
     * (VIGNETTE_MOTO_&lt;NOM_STATUT&gt;), contrat "Suivi d'evolution des demandes de vignettes",
     * 2026-09-17. */
    vignette_moto_changement_statut,
    /** Distinct du precedent : ne se declenche pas a un changement de statut mais a la regeneration
     * d'un lien de suivi (LienSuiviVignetteService.regenerer) - seul moment ou le token brut existe
     * de nouveau en memoire pour etre inclus dans le SMS (cf. javadoc LienSuiviVignette : le token
     * n'est jamais persiste en clair, seul son hash SHA-256 l'est). */
    vignette_moto_lien_suivi_regenere,
    /** Rappel periodique au partenaire (frequence HEBDOMADAIRE/QUINZAINE/MENSUELLE, 2026-09-23) :
     * commissions disponibles a demander depuis l'espace partenaire. */
    commission_disponible,
    /** Partenaire A_LA_DEMANDE (2026-09-23) : une nouvelle commission vient d'etre gagnee. */
    commission_gagnee
}
