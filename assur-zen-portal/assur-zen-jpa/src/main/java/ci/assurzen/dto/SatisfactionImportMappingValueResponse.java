package ci.assurzen.dto;

public record SatisfactionImportMappingValueResponse(
        Long id,
        Long mappingId,
        String valeurSource,
        String valeurCible
) {
}
