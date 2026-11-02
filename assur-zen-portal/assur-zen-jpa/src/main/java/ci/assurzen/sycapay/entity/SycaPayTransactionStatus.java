package ci.assurzen.sycapay.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cycle de vie d'une transaction de paiement SycaPay (section 13 de la specification).
 *
 * <pre>
 * PENDING    -&gt; PROCESSING | SUCCESS | FAILED | UNKNOWN
 * PROCESSING -&gt; SUCCESS | FAILED | UNKNOWN
 * UNKNOWN    -&gt; SUCCESS | FAILED            (uniquement via reconciliation getStatusByOrderID)
 * FAILED     -&gt; PENDING                     (uniquement via une nouvelle tentative EXPLICITE, jamais automatique)
 * SUCCESS, CANCELLED : etats terminaux, aucune transition sortante.
 * </pre>
 */
public enum SycaPayTransactionStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED,
    UNKNOWN;

    private static final Set<SycaPayTransactionStatus> TERMINAL = EnumSet.of(SUCCESS, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(SycaPayTransactionStatus target) {
        if (this.isTerminal()) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == PROCESSING || target == SUCCESS || target == FAILED || target == UNKNOWN;
            case PROCESSING -> target == SUCCESS || target == FAILED || target == UNKNOWN;
            case UNKNOWN -> target == SUCCESS || target == FAILED;
            case FAILED -> target == PENDING;
            default -> false;
        };
    }
}
