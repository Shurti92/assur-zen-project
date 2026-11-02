package ci.assurzen.mapper;

import ci.assurzen.dto.SatisfactionImportMappingProfileResponse;
import ci.assurzen.entities.SatisfactionImportMappingProfile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SatisfactionImportMappingProfileMapper {

    SatisfactionImportMappingProfileResponse toResponse(SatisfactionImportMappingProfile profile, long nbMappings);
}
