package ci.assurzen.dto;

import java.time.Instant;

public record CommercialCommissionPaymentResponse(
        Long id,
        Long contractId,
        String policyNumber,
        Long commercialId,
        String commercialNom,
        Long amount,
        Long paidByUserId,
        String paidByUsername,
        String reference,
        Instant paidAt,
        /** Present uniquement sur la reponse de pay() - null sur les lectures ulterieures
         * (getPaymentHistory), meme convention que CommissionPaymentResponse.notificationEnvoyee. */
        Boolean notificationEnvoyee,
        /**
         * Ajoute par l'integration SycaPay (2026-09-22) - present uniquement sur la reponse de pay(),
         * meme convention que notificationEnvoyee. Reflete l'issue REELLE du virement Wave pour ce
         * contrat : "SUCCESS" signifie que id/paidAt sont renseignes (paiement effectivement enregistre) ;
         * toute autre valeur ("PROCESSING", "FAILED", "UNKNOWN", "REJECTED") signifie qu'AUCUN paiement
         * n'a ete enregistre pour ce contrat malgre sa presence dans la requete - le frontend DOIT lire ce
         * champ au lieu de supposer que chaque contrat demande a ete paye avec succes.
         */
        String sycapayStatus,
        String sycapayTransactionReference,
        String sycapayMessage,
        /**
         * Ajoute le 2026-09-23 a la demande du frontend : id technique (cle primaire) de la
         * SycaPayTransaction, pour appeler directement POST /sycapay/transactions/{id}/reconcile sur un
         * contrat UNKNOWN/PROCESSING. Renseigne pour SUCCESS/PROCESSING/FAILED/UNKNOWN, null pour REJECTED
         * (aucune transaction creee) et sur les lectures historiques - meme regle que
         * sycapayTransactionReference. Nomme "Pk" et non "sycapayTransactionId" : ce dernier nom designe
         * deja l'identifiant cote SycaPay (String) sur SycaPayTransactionResponse.
         */
        Long sycapayTransactionPk,
        /** VIREMENT_SYCAPAY (virement Wave par SycaPay) | ENREGISTREMENT_MANUEL (paiement fait hors
         * application puis enregistre - SycaPay desactive, ou paiement anterieur), 2026-09-26. */
        String modePaiement
) {
}
