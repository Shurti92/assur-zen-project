package ci.assurzen.mapper;

import ci.assurzen.dto.CrossSellRuleConditionResponse;
import ci.assurzen.entities.CrossSellRuleCondition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CrossSellRuleConditionMapper {

    @Mapping(target = "regleId", source = "regle.id")
    CrossSellRuleConditionResponse toResponse(CrossSellRuleCondition condition);
}
