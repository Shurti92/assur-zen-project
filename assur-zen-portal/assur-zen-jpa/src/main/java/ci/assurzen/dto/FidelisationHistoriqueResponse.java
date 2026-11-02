package ci.assurzen.dto;

import ci.assurzen.entities.MouvementFideliteType;

import java.time.Instant;

public record FidelisationHistoriqueResponse(
        Long id,
        Long compteFideliteId,
        MouvementFideliteType typeMouvement,
        Long points,
        Long soldeApres,
        String motif,
        Long contratId,
        String contratPolicyNumber,
        Long auteurId,
        String auteur,
        Instant createdAt
) {
}
