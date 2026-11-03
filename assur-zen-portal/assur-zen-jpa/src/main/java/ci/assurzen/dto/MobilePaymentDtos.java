package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Paiement depuis l'application mobile (demande n°4, 2026-11-02) - simulation en dev. */
public final class MobilePaymentDtos {

    private MobilePaymentDtos() {
    }

    public record Method(String id, String name, String subtitle, boolean needsPhoneNumber, boolean enabled, boolean simulated) {
    }

    public record MethodList(List<Method> items) {
    }

    public record InitiateRequest(
            @NotBlank(message = "La reference a payer est obligatoire") @Size(max = 60) String payableReference,
            @NotBlank(message = "Le moyen de paiement est obligatoire") @Size(max = 20) String methodId,
            @Size(max = 30) String phone,
            /** Montant affiche par l'app, pour controle uniquement : le serveur decide (409 AMOUNT_MISMATCH si different). */
            Long amount,
            @Size(max = 100) String idempotencyKey
    ) {
    }

    public record Payment(
            String reference,
            String status,
            String failureReason,
            String receiptUrl,
            String payableReference,
            Long amount,
            String methodId,
            boolean simulated,
            /** Lien a ouvrir pour confirmer (fournisseur reel) ; null en simulation. */
            String redirectUrl,
            /** Consigne a afficher a l'utilisateur. */
            String instruction,
            Instant createdAt,
            Instant expiresAt
    ) {
    }
}
