package ci.assurzen.dto;

import ci.assurzen.entities.PaymentRequestType;
import jakarta.validation.constraints.NotNull;

/** POST /commercials/me/commissions/payment-requests (2026-09-25). amount : ignore pour TOTAL,
 * obligatoire pour PARTIAL (revalide cote serveur, jamais pris tel quel). */
public record CommercialPaymentRequestCreateRequest(
        @NotNull PaymentRequestType type,
        Long amount
) {
}
