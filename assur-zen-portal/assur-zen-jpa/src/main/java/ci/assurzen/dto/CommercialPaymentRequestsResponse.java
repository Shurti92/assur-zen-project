package ci.assurzen.dto;

import java.util.List;

/** GET /commercials/me/commissions/payment-requests (2026-09-25). availableBalance = commission
 * commerciale en attente MOINS les demandes deja accordees (pending ou paid) ; history tri decroissant. */
public record CommercialPaymentRequestsResponse(
        long availableBalance,
        List<CommercialPaymentRequestResponse> history
) {
}
