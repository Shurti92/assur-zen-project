package ci.assurzen.dto;

public record FleetImportErrorResponse(
        Long id,
        Integer ligneNumero,
        String colonneEnErreur,
        String valeurBrute,
        String message
) {
}
