package ci.assurzen.dto;

import ci.assurzen.entities.CommissionStatus;

import java.time.Instant;

public record VignetteCommissionResponse(
        Long id,
        Long demandeVignetteMotoId,
        String demandeVignetteMotoReference,
        Long partenaireId,
        String partenaireNom,
        Long commercialId,
        String commercialNom,
        Long montant,
        CommissionStatus statut,
        Long paidByUserId,
        String paidByUsername,
        String reference,
        Instant paidAt,
        Instant createdAt,
        /** Present uniquement sur la reponse de payer() - null sur les lectures ulterieures (list),
         * meme convention que CommissionPaymentResponse/CommercialCommissionPaymentResponse. */
        Boolean notificationEnvoyee
) {
}
