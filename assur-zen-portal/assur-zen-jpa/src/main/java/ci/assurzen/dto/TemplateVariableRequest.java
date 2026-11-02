package ci.assurzen.dto;

import ci.assurzen.entities.VariableDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TemplateVariableRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[a-z0-9_]+$", message = "code en minuscules/chiffres/underscore uniquement (ex: nom_client)") String code,
        @NotBlank @Size(max = 100) String libelle,
        @Size(max = 2000) String description,
        @NotNull VariableDataType typeDonnee,
        Boolean active
) {
}
