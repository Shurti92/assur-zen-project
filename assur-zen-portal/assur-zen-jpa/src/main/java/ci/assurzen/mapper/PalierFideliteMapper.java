package ci.assurzen.mapper;

import ci.assurzen.dto.PalierFideliteResponse;
import ci.assurzen.entities.PalierFidelite;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PalierFideliteMapper {

    PalierFideliteResponse toResponse(PalierFidelite palier);
}
