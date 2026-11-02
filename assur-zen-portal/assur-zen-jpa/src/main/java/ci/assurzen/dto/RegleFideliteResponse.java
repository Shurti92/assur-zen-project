package ci.assurzen.dto;

import ci.assurzen.entities.RegleFideliteChamp;
import ci.assurzen.entities.SegmentRuleOperator;

public record RegleFideliteResponse(
        Long id,
        String code,
        String nom,
        RegleFideliteChamp champ,
        SegmentRuleOperator operateur,
        String valeur,
        Long poids,
        Integer ordre,
        boolean actif
) {
}
