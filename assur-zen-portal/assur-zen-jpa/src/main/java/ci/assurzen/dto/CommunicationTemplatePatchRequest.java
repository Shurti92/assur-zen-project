package ci.assurzen.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** Mise a jour partielle : seuls les champs non-null sont appliques. */
public record CommunicationTemplatePatchRequest(
        @Size(max = 150) String nom,
        @Size(max = 4000) String description,
        Long categorieId,
        Long langueId,
        @Size(max = 200) String objet,
        String contenu,
        Integer maxCaracteres,
        List<String> variableCodes,
        Boolean active
) {
}
