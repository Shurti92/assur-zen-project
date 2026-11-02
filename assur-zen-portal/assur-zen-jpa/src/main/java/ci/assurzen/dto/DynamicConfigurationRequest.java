package ci.assurzen.dto;

import ci.assurzen.entities.ConfigCategory;
import ci.assurzen.entities.ConfigEnvironment;
import ci.assurzen.entities.ConfigValueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DynamicConfigurationRequest(
        @NotBlank @Size(max = 100) String configKey,
        String configValue,
        @NotNull ConfigValueType valueType,
        String description,
        @Size(max = 50) String module,
        ConfigCategory category,
        ConfigEnvironment environment,
        String defaultValue,
        @Size(max = 300) String validationRule,
        String allowedValues,
        Boolean active,
        Boolean editable,
        Boolean sensitive,
        Boolean restartRequired,
        Boolean cacheEnabled,
        Integer displayOrder,
        /** Raison du changement, pour l'audit uniquement (voir HistoriqueModification.motif) - jamais persiste sur l'entite elle-meme. */
        String motif
) {
}
