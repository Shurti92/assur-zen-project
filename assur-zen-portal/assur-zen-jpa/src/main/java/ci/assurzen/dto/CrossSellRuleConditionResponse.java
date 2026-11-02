package ci.assurzen.dto;

import ci.assurzen.entities.CrossSellChamp;
import ci.assurzen.entities.SegmentRuleOperator;

public record CrossSellRuleConditionResponse(
        Long id,
        Long regleId,
        CrossSellChamp champ,
        SegmentRuleOperator operateur,
        String valeur,
        Integer ordre
) {
}
