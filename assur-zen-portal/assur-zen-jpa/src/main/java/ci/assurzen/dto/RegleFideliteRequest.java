package ci.assurzen.dto;

import ci.assurzen.entities.RegleFideliteChamp;
import ci.assurzen.entities.SegmentRuleOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegleFideliteRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String nom,
        @NotNull RegleFideliteChamp champ,
        @NotNull SegmentRuleOperator operateur,
        @NotBlank @Size(max = 100) String valeur,
        @NotNull Long poids,
        Integer ordre,
        Boolean actif
) {
}
