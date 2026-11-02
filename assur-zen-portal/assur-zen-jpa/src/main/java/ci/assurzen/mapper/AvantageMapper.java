package ci.assurzen.mapper;

import ci.assurzen.dto.AvantageResponse;
import ci.assurzen.entities.Avantage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AvantageMapper {

    @Mapping(target = "palierId", source = "palier.id")
    @Mapping(target = "palierLibelle", source = "palier.libelle")
    AvantageResponse toResponse(Avantage avantage);
}
