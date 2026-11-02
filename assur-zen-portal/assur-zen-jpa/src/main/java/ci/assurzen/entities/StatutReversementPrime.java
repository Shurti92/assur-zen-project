package ci.assurzen.entities;

/**
 * Suivi du reversement, par le partenaire a la compagnie, de la prime qu'il a encaissee aupres du
 * client (sens inverse de CommissionStatus, qui suit le versement de la commission par la compagnie
 * au partenaire). Bascule exclusivement via validation manuelle admin/finance
 * (ContractsService.reverserPrime) - pas de detection automatique.
 */
public enum StatutReversementPrime {
    non_reverse,
    reverse
}
