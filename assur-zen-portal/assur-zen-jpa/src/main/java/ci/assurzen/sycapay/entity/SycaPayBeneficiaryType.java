package ci.assurzen.sycapay.entity;

/** Beneficiaire d'un virement SycaPay (2026-09-23) : commercial interne (commission commerciale,
 * paiement declenche par un admin) ou partenaire d'affaire (commission partenaire, paiement
 * automatique si Partenaire.frequencePaiementCommission = IMMEDIAT). */
public enum SycaPayBeneficiaryType {
    COMMERCIAL,
    PARTENAIRE
}
