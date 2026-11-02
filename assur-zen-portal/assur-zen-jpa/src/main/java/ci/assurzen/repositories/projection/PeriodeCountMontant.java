package ci.assurzen.repositories.projection;

import java.math.BigDecimal;

/** Point d'une serie d'evolution temporelle (§6/§13) : periode deja formatee cote SQL (DATE_FORMAT,
 * granularite jour/semaine/mois choisie par la methode de requete appelee), nombre et montant agreges
 * sur ce point. */
public interface PeriodeCountMontant {
    String getPeriode();
    Long getTotal();
    BigDecimal getMontant();
}
