package ci.assurzen.dto;

import ci.assurzen.entities.PartialPaymentRequestStatus;
import ci.assurzen.entities.PaymentRequestType;

import java.time.Instant;

/** Une demande de paiement de commission d'un commercial interne (2026-09-25). */
public record CommercialPaymentRequestResponse(
        Long id,
        PaymentRequestType type,
        Long requestedAmount,
        Instant requestedAt,
        PartialPaymentRequestStatus status
) {
}
