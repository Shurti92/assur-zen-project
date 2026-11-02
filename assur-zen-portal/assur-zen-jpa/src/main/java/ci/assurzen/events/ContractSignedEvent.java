package ci.assurzen.events;

/** Publie quand un contrat passe au statut "signed" (creation ou modification, ContractsService),
 * 2026-09-23 : declenche le traitement des commissions partenaire selon leur frequence de paiement
 * (paiement automatique IMMEDIAT, notification A_LA_DEMANDE). Traite APRES commit, de facon
 * asynchrone : ne peut jamais bloquer ni annuler l'enregistrement du contrat. */
public record ContractSignedEvent(Long contractId) {
}
