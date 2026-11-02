package ci.assurzen.dto;

import ci.assurzen.entities.SatisfactionImportField;

public record SatisfactionImportMappingResponse(
        Long id,
        Long profileId,
        String csvColumnName,
        SatisfactionImportField targetField,
        boolean obligatoire,
        Integer ordre
) {
}
