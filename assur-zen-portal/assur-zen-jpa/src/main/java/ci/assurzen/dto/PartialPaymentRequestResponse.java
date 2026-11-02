package ci.assurzen.dto;

import ci.assurzen.entities.PartialPaymentRequestStatus;

import java.time.Instant;

public record PartialPaymentRequestResponse(
        Long id,
        Long partenaireId,
        Long requestedAmount,
        Long pendingBalanceBefore,
        Long pendingBalanceAfter,
        Instant requestedAt,
        PartialPaymentRequestStatus status,
        /** Numero Wave fige au moment de la demande (audit de securite du 2026-08-23) - reste
         * identique meme si le numero est modifie sur la fiche partenaire apres coup. */
        String numeroWaveCommission,
        /** Demande d'un commercial interne (2026-09-25) : commercialId renseigne, partenaireId null ;
         * type TOTAL | PARTIAL (null pour un partenaire). numeroWaveCommission = telephone du commercial. */
        Long commercialId,
        ci.assurzen.entities.PaymentRequestType type,
        /** Virement SycaPay de la demande (2026-09-26) - null sans virement. sycapayStatus : SUCCESS |
         * PENDING | PROCESSING | FAILED | UNKNOWN, ou REJECTED (aucune tentative, sur la reponse de valider). */
        String sycapayStatus,
        String sycapayTransactionReference,
        Long sycapayTransactionPk,
        String sycapayMessage,
        /** VIREMENT_SYCAPAY | ENREGISTREMENT_MANUEL (payee hors application) | null (pas encore payee). */
        String modePaiement
) {
}
