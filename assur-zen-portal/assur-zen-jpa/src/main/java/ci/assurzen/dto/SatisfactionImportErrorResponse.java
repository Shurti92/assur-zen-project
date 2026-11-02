package ci.assurzen.dto;

import ci.assurzen.entities.ImportErrorType;

import java.time.Instant;

public record SatisfactionImportErrorResponse(
        Long id,
        Long importId,
        Integer ligneNumero,
        String colonneEnErreur,
        String valeurBrute,
        ImportErrorType typeErreur,
        String message,
        Instant createdAt
) {
}
