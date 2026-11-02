package ci.assurzen.mapper;

import ci.assurzen.dto.RegleFideliteResponse;
import ci.assurzen.entities.RegleFidelite;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RegleFideliteMapper {

    RegleFideliteResponse toResponse(RegleFidelite regle);
}
