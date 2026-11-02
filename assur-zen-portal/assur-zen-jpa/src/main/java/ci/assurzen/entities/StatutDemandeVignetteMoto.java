package ci.assurzen.entities;

/**
 * Workflow prepare pour les phases futures (paiement/edition/livraison, hors perimetre de ce lot) -
 * seules les transitions BROUILLON -> EN_ATTENTE_PAIEMENT (soumettre), ANNULEE et REJETEE sont
 * implementees par DemandeVignetteMotoService. Les autres valeurs existent pour que le contrat
 * d'API n'ait pas a changer quand ces phases seront construites.
 */
public enum StatutDemandeVignetteMoto {
    BROUILLON,
    EN_ATTENTE_PAIEMENT,
    PAYEE,
    EN_TRAITEMENT,
    EN_EDITION,
    EDITEE,
    EN_LIVRAISON,
    LIVREE,
    ANNULEE,
    REJETEE
}
