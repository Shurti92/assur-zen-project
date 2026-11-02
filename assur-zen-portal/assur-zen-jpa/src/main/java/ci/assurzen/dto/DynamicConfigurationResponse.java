package ci.assurzen.dto;

import ci.assurzen.entities.ConfigCategory;
import ci.assurzen.entities.ConfigEnvironment;
import ci.assurzen.entities.ConfigValueType;

import java.time.Instant;

/** configValue vaut "********" quand sensitive=true - jamais la valeur en clair ni chiffree. */
public record DynamicConfigurationResponse(
        Long id,
        String uuid,
        String configKey,
        String configValue,
        ConfigValueType valueType,
        String description,
        String module,
        ConfigCategory category,
        ConfigEnvironment environment,
        String defaultValue,
        String validationRule,
        String allowedValues,
        boolean active,
        boolean editable,
        boolean sensitive,
        boolean restartRequired,
        boolean cacheEnabled,
        Integer displayOrder,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        Long version
) {
}
