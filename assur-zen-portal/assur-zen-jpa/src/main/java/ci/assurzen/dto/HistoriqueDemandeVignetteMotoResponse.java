package ci.assurzen.dto;

import ci.assurzen.entities.StatutDemandeVignetteMoto;

import java.time.Instant;

public record HistoriqueDemandeVignetteMotoResponse(
        Long id,
        Long demandeVignetteMotoId,
        StatutDemandeVignetteMoto ancienStatut,
        StatutDemandeVignetteMoto nouveauStatut,
        String motif,
        String referenceOperation,
        Long auteurId,
        String auteurNom,
        Boolean notificationEnvoyee,
        Instant createdAt
) {
}
