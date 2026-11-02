package ci.assurzen.entities;

/**
 * Flux synchrone (decision produit) : preview() aboutit directement a READY ou FAILED ; confirm()
 * passe par PROCESSING (transitoire, pour coherence/observabilite) puis COMPLETED/
 * PARTIALLY_COMPLETED/FAILED. CANCELLED reste dans l'enum pour completude du schema mais aucune
 * action dediee n'existe cette phase - ne pas confirmer un import READY equivaut deja a l'annuler.
 */
public enum FleetImportStatus {
    UPLOADED,
    VALIDATING,
    READY,
    PROCESSING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    CANCELLED
}
