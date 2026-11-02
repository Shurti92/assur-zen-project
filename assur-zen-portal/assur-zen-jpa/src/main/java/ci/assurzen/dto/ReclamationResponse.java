package ci.assurzen.dto;

import ci.assurzen.entities.ReclamationCanal;
import ci.assurzen.entities.ReclamationPriorite;
import ci.assurzen.entities.ReclamationStatut;
import ci.assurzen.entities.ReclamationType;

import java.time.Instant;

public record ReclamationResponse(
        Long id,
        String numero,
        Long insuredId,
        String insuredName,
        Long contratId,
        String contratPolicyNumber,
        Long produitId,
        String produitNom,
        Long sinistreId,
        String sinistreNumero,
        Long enqueteId,
        String enqueteCode,
        ReclamationCanal canal,
        ReclamationType type,
        ReclamationPriorite priorite,
        ReclamationStatut statut,
        String description,
        Long gestionnaireId,
        String gestionnaireNom,
        String service,
        Instant dateAffectation,
        Instant dateResolution,
        Instant dateCloture,
        String motifCloture,
        boolean creeeAutomatiquement,
        Boolean notificationEnvoyee,
        Instant createdAt,
        Instant lastUpdate
) {
}
