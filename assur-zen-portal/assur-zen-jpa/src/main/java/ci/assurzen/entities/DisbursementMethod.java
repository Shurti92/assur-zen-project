package ci.assurzen.entities;

/** Canal de decaissement/remboursement. Distinct de ModePaiement (COMPTANT/FRACTIONNE, qui concerne
 * le fractionnement des primes de contrat - un tout autre concept) : aucun referentiel de moyens de
 * paiement n'existait dans ce projet avant ce module. */
public enum DisbursementMethod {
    BANK_TRANSFER,
    ORANGE_MONEY,
    MTN_MONEY,
    MOOV_MONEY,
    WAVE,
    CASH,
    CHEQUE,
    OTHER
}
