package ci.assurzen.mapper;

import ci.assurzen.dto.SatisfactionImportMappingValueResponse;
import ci.assurzen.entities.SatisfactionImportMappingValue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SatisfactionImportMappingValueMapper {

    @Mapping(target = "mappingId", source = "mapping.id")
    SatisfactionImportMappingValueResponse toResponse(SatisfactionImportMappingValue value);
}
