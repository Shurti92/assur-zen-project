package ci.assurzen.mapper;

import ci.assurzen.dto.RecompenseResponse;
import ci.assurzen.entities.Recompense;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RecompenseMapper {

    RecompenseResponse toResponse(Recompense recompense);
}
