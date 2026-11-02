package ci.assurzen.dto;

import jakarta.validation.constraints.Size;

/** Paiement du bonus mensuel d'un commercial (2026-10-02) - reference libre optionnelle (piece, virement
 * hors application...), motif d'audit optionnel. */
public record CommercialBonusPaymentRequest(
        @Size(max = 100) String reference,
        @Size(max = 500) String motif
) {
}
