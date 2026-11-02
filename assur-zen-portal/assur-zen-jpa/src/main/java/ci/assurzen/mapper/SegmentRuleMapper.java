package ci.assurzen.mapper;

import ci.assurzen.dto.SegmentRuleResponse;
import ci.assurzen.entities.SegmentRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SegmentRuleMapper {

    @Mapping(target = "segmentId", source = "segment.id")
    SegmentRuleResponse toResponse(SegmentRule rule);
}
