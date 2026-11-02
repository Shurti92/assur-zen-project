package ci.assurzen.mapper;

import ci.assurzen.dto.SatisfactionImportMappingResponse;
import ci.assurzen.entities.SatisfactionImportMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SatisfactionImportMappingMapper {

    @Mapping(target = "profileId", source = "profile.id")
    SatisfactionImportMappingResponse toResponse(SatisfactionImportMapping mapping);
}
