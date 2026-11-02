package ci.assurzen.mapper;

import ci.assurzen.dto.SatisfactionImportResponse;
import ci.assurzen.entities.SatisfactionImport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = MappingSupport.class)
public interface SatisfactionImportMapper {

    @Mapping(target = "mappingProfileId", source = "mappingProfile.id")
    @Mapping(target = "mappingProfileNom", source = "mappingProfile.nom")
    @Mapping(target = "executeParId", source = "executePar.id")
    @Mapping(target = "executePar", source = "executePar", qualifiedByName = "userDisplayName")
    SatisfactionImportResponse toResponse(SatisfactionImport satisfactionImport);
}
