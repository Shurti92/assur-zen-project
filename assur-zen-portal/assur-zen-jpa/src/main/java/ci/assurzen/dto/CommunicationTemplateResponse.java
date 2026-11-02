package ci.assurzen.dto;

import ci.assurzen.entities.CanalEnvoi;
import ci.assurzen.entities.TemplateStatut;

import java.time.Instant;
import java.util.List;

public record CommunicationTemplateResponse(
        Long id,
        String uuid,
        String code,
        String nom,
        String description,
        CanalEnvoi canal,
        Long categorieId,
        String categorieLibelle,
        Long langueId,
        String langueLibelle,
        String objet,
        String contenu,
        TemplateStatut statut,
        Integer maxCaracteres,
        boolean active,
        List<String> variableCodes,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {
}
