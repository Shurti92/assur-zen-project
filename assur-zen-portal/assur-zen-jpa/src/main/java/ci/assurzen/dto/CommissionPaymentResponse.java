package ci.assurzen.dto;

import java.time.Instant;

public record CommissionPaymentResponse(
        Long id,
        Long contractId,
        String policyNumber,
        Long partenaireId,
        String partenaireNom,
        Long amount,
        Long paidByUserId,
        String paidByUsername,
        String reference,
        Instant paidAt,
        /** Present uniquement sur la reponse de pay() (un seul SMS agrege par partenaire, pas par
         * contrat) - null sur les lectures ulterieures (getPaymentHistory), meme convention que
         * UserProfileResponse.notificationEnvoyee. */
        Boolean notificationEnvoyee,
        /** Paiement automatique SycaPay (2026-09-23) : true si verse automatiquement par virement Wave
         * (partenaire IMMEDIAT), false si enregistre manuellement par un admin. */
        boolean automatic,
        /** Champs SycaPay (2026-09-23), renseignes pour tout paiement par virement Wave (automatique, ou
         * manuel depuis le 2026-09-26) - null pour un paiement enregistre hors application.
         * sycapayTransactionPk = id de /sycapay/transactions/{id}. Reponse de POST /commissions/pay par
         * virement : une entree par contrat, sycapayStatus SUCCESS | PROCESSING | FAILED | UNKNOWN | REJECTED,
         * et id/paidAt/paidBy/reference null si le statut n'est pas SUCCESS (aucun paiement enregistre). */
        String sycapayStatus,
        String sycapayTransactionReference,
        Long sycapayTransactionPk,
        String sycapayMessage,
        /** VIREMENT_SYCAPAY (virement Wave, automatique ou manuel) | ENREGISTREMENT_MANUEL (paiement fait
         * hors application puis enregistre - SycaPay desactive, ou paiement anterieur) | AVANCE_IMPUTEE
         * (entierement couvert par des avances deja versees, aucun nouveau versement), 2026-09-26. */
        String modePaiement,
        /** Part de amount deja versee au partenaire par des demandes de paiement partiel, deduite de ce
         * paiement (2026-09-26) - null si aucune (ou paiement non enregistre). */
        Long avanceImputee,
        /** Montant reellement verse (virement) ou enregistre pour ce contrat : amount - avanceImputee.
         * null si aucun paiement n'a ete enregistre (virement non SUCCESS). */
        Long montantVerse
) {
}
