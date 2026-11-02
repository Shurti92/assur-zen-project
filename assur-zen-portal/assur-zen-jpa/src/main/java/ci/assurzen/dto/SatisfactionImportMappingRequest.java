package ci.assurzen.dto;

import ci.assurzen.entities.SatisfactionImportField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SatisfactionImportMappingRequest(
        @NotBlank @Size(max = 150) String csvColumnName,
        @NotNull SatisfactionImportField targetField,
        Boolean obligatoire,
        Integer ordre
) {
}
