package ci.assurzen.repositories.projection;

/** Comme PeriodeCountMontant mais sans montant - evolution des nouveaux assures (§13), qui n'a pas de
 * composante financiere. */
public interface PeriodeCount {
    String getPeriode();
    Long getTotal();
}
