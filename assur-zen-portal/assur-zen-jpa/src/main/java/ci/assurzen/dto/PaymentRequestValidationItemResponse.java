package ci.assurzen.dto;

import ci.assurzen.entities.PartialPaymentRequestStatus;
import ci.assurzen.entities.PaymentRequestType;

import java.time.Instant;

/**
 * Ecran de validation admin (GET /commissions/partial-payment-requests, 2026-09-25) : demandes des
 * partenaires ET des commerciaux internes. beneficiaryType = "PARTENAIRE" | "COMMERCIAL" ;
 * telephoneVersement = numero fige au moment de la demande (Wave du partenaire, telephone du commercial).
 * type : TOTAL | PARTIAL pour un commercial, null pour un partenaire.
 */
public record PaymentRequestValidationItemResponse(
        Long id,
        String beneficiaryType,
        Long partenaireId,
        Long commercialId,
        String beneficiaireNom,
        String telephoneVersement,
        PaymentRequestType type,
        Long requestedAmount,
        Instant requestedAt,
        PartialPaymentRequestStatus status,
        Instant validatedAt,
        /** Virement SycaPay de la demande (2026-09-27), memes regles que PartialPaymentRequestResponse -
         * null sans virement. sycapayTransactionPk : pour POST /sycapay/transactions/{id}/reconcile. */
        String sycapayStatus,
        Long sycapayTransactionPk,
        String sycapayMessage,
        /** VIREMENT_SYCAPAY | ENREGISTREMENT_MANUEL | null (pas encore payee). */
        String modePaiement
) {
}
