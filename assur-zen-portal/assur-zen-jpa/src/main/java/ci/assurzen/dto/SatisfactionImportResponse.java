package ci.assurzen.dto;

import ci.assurzen.entities.DuplicateStrategy;
import ci.assurzen.entities.SatisfactionImportStatut;

import java.time.Instant;

public record SatisfactionImportResponse(
        Long id,
        String uuid,
        String code,
        Long mappingProfileId,
        String mappingProfileNom,
        String source,
        String surveyId,
        String fileName,
        String delimiter,
        String encoding,
        boolean hasHeader,
        DuplicateStrategy duplicateStrategy,
        SatisfactionImportStatut statut,
        Integer totalLignes,
        Integer lignesIgnorees,
        Integer lignesReussies,
        Integer lignesEnErreur,
        Instant dateDebutTraitement,
        Instant dateFinTraitement,
        Long executeParId,
        String executePar,
        Instant createdAt
) {
}
