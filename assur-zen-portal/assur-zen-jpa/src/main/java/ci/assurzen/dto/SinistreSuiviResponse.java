package ci.assurzen.dto;

import ci.assurzen.entities.SinistreStatus;

import java.time.Instant;

public record SinistreSuiviResponse(
        Long id,
        Long sinistreId,
        SinistreStatus statut,
        String commentaire,
        Long auteurId,
        String auteur,
        boolean notificationEnvoyee,
        Instant createdAt
) {
}
