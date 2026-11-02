package ci.assurzen.dto;

import ci.assurzen.entities.PartenaireNotificationType;

import java.time.Instant;

public record PartenaireNotificationResponse(
        Long id,
        PartenaireNotificationType type,
        String titre,
        String message,
        Long montant,
        Long contractId,
        boolean smsEnvoye,
        boolean lu,
        Instant luAt,
        Instant createdAt,
        /** NONE | PENDING | SENT | FAILED, null pour une notification anterieure au push (2026-09-26). */
        ci.assurzen.entities.PushStatus pushStatus,
        /** Objet a ouvrir depuis la notification (2026-10-03) : CONTRAT | DEMANDE_PAIEMENT |
         * PERFORMANCE_COMMERCIALE | null (rappel sans objet precis). */
        String objetType,
        Long objetId,
        /** Route interne de l'application EP quand le backend la connait (ex. "/commissions"), sinon null. */
        String route
) {
}
