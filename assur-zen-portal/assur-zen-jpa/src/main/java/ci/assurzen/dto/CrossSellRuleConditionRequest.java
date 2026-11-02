package ci.assurzen.dto;

import ci.assurzen.entities.CrossSellChamp;
import ci.assurzen.entities.SegmentRuleOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrossSellRuleConditionRequest(
        @NotNull CrossSellChamp champ,
        @NotNull SegmentRuleOperator operateur,
        @NotBlank @Size(max = 100) String valeur,
        Integer ordre
) {
}
