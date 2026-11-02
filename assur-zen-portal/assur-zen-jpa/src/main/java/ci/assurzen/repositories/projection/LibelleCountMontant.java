package ci.assurzen.repositories.projection;

import java.math.BigDecimal;

/** Meme role que LibelleCount, avec un montant agrege en plus (repartitions financieres : par type de
 * sortie de fonds, par beneficiaire...). */
public interface LibelleCountMontant {
    String getLibelle();
    Long getTotal();
    BigDecimal getMontant();
}
