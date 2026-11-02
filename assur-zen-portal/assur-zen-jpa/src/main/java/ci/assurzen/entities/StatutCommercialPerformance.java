package ci.assurzen.entities;

/**
 * Workflow mensuel de CommercialPerformance (contrat "Module Commercial", 2026-09-07) :
 * OPEN -&gt; CALCULATED -&gt; ELIGIBLE|NOT_ELIGIBLE -&gt; VALIDATED -&gt; PAID -&gt; CLOSED. Recalcul libre tant que
 * le statut n'a pas atteint VALIDATED (cf. CommercialPerformanceService.recalculer) ; VALIDATED/PAID/CLOSED
 * sont verrouilles (memes garde-fous que StatutPrimeObjectif).
 */
public enum StatutCommercialPerformance {
    OPEN,
    CALCULATED,
    ELIGIBLE,
    NOT_ELIGIBLE,
    VALIDATED,
    PAID,
    CLOSED
}
