package ci.assurzen.dto;

import ci.assurzen.entities.LogicalOperator;
import ci.assurzen.entities.SegmentRuleField;
import ci.assurzen.entities.SegmentRuleOperator;

public record SegmentRuleResponse(
        Long id,
        Long segmentId,
        SegmentRuleField champ,
        SegmentRuleOperator operateur,
        String valeur,
        Integer ordre,
        Integer groupe,
        LogicalOperator operateurLogique
) {
}
