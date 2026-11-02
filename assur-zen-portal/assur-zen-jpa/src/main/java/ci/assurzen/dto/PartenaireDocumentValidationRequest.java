package ci.assurzen.dto;

import ci.assurzen.entities.StatutValidationDocument;

/** PATCH /partenaires/{id}/documents/{documentId}/validation (admin, 2026-10-04) : VALIDE, ou REFUSE avec motif. */
public record PartenaireDocumentValidationRequest(
        StatutValidationDocument statut,
        String motif
) {
}
