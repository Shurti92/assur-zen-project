package ci.assurzen.mapper;

import ci.assurzen.dto.SegmentResponse;
import ci.assurzen.entities.Segment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SegmentMapper {

    SegmentResponse toResponse(Segment segment, long nbRegles, long nbMembres);
}
