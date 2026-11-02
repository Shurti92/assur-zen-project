package ci.assurzen.dto;

import ci.assurzen.entities.CanalEnvoi;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CommunicationTemplateRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 4000) String description,
        @NotNull CanalEnvoi canal,
        @NotNull Long categorieId,
        @NotNull Long langueId,
        @Size(max = 200) String objet,
        @NotBlank String contenu,
        Integer maxCaracteres,
        List<String> variableCodes,
        Boolean active
) {
}
