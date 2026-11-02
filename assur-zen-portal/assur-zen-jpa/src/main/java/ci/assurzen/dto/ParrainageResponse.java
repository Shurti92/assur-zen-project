package ci.assurzen.dto;

import ci.assurzen.entities.StatutParrainage;

import java.time.Instant;

public record ParrainageResponse(
        Long id,
        String reference,
        Long programmeId,
        String programmeNom,
        Long codeParrainageId,
        String codeUtilise,
        Long parrainId,
        String parrainNom,
        Long filleulId,
        String filleulNom,
        String filleulEmail,
        String filleulTelephone,
        Long contratId,
        String contratPolicyNumber,
        StatutParrainage statut,
        String motifRejet,
        Instant dateInvitation,
        Instant dateInscription,
        Instant dateEligibilite,
        Instant dateCompletion,
        Instant dateAnnulation,
        Instant dateExpiration,
        Instant createdAt,
        Instant lastUpdate
) {
}
