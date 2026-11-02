package ci.assurzen.dto;

import java.time.Instant;

public record PartenaireDocumentResponse(
        Long id,
        Long partenaireId,
        String type,
        String fileName,
        String contentType,
        Long sizeBytes,
        String uploadedBy,
        Instant createdAt,
        /** Fin de validite du document, null si inconnue (2026-10-03). */
        java.time.LocalDate dateExpiration,
        /** true si dateExpiration est passee (strictement avant aujourd'hui). */
        boolean expire,
        /** EN_ATTENTE | VALIDE | REFUSE (2026-10-04) - EN_ATTENTE pour les documents anterieurs. */
        ci.assurzen.entities.StatutValidationDocument statut,
        /** Motif du refus, null sinon. */
        String motifRefus,
        /** Date de validation ou de refus, null si EN_ATTENTE. */
        Instant valideLe,
        /** Nom de l'administrateur qui a valide ou refuse. */
        String validePar
) {
}
