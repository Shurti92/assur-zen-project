package ci.assurzen.mapper;

import ci.assurzen.dto.LangueResponse;
import ci.assurzen.entities.Langue;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LangueMapper {

    LangueResponse toResponse(Langue langue);
}
