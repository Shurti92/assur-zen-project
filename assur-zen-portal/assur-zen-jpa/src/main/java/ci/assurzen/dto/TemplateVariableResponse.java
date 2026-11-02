package ci.assurzen.dto;

import ci.assurzen.entities.VariableDataType;

public record TemplateVariableResponse(
        Long id,
        String code,
        String libelle,
        String description,
        VariableDataType typeDonnee,
        boolean active
) {
}
