package ci.assurzen.dto;

import java.time.Instant;

public record ReclamationCommentaireResponse(
        Long id,
        Long reclamationId,
        String contenu,
        Long auteurId,
        String auteurNom,
        Instant createdAt
) {
}
