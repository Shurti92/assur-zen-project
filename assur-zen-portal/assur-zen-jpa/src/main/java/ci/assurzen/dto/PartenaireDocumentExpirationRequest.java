package ci.assurzen.dto;

import java.time.LocalDate;

/** PATCH /partenaires/{id}/documents/{documentId} : date d'expiration du document, null pour l'effacer (2026-10-03). */
public record PartenaireDocumentExpirationRequest(
        LocalDate dateExpiration
) {
}
