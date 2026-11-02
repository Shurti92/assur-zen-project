package ci.assurzen.mapper;

import ci.assurzen.dto.SegmentMembreResponse;
import ci.assurzen.entities.SegmentMembre;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = MappingSupport.class)
public interface SegmentMembreMapper {

    @Mapping(target = "segmentId", source = "segment.id")
    @Mapping(target = "insuredId", source = "insured.id")
    @Mapping(target = "insuredName", source = "insured", qualifiedByName = "insuredDisplayName")
    SegmentMembreResponse toResponse(SegmentMembre membre);
}
