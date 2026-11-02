package ci.assurzen.dto;

import ci.assurzen.entities.LogicalOperator;
import ci.assurzen.entities.SegmentRuleField;
import ci.assurzen.entities.SegmentRuleOperator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SegmentRuleRequest(
        @NotNull SegmentRuleField champ,
        @NotNull SegmentRuleOperator operateur,
        @Size(max = 500) String valeur,
        Integer ordre,
        Integer groupe,
        LogicalOperator operateurLogique
) {
}
