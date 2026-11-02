package ci.assurzen.entities;

/**
 * EXPIRED est prevu pour compatibilite future (echeance de flotte) mais aucune transition
 * automatique par date n'est implementee cote service pour l'instant - ce serait un job planifie,
 * hors perimetre de ce module en l'etat.
 */
public enum FleetStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    CLOSED,
    EXPIRED
}
