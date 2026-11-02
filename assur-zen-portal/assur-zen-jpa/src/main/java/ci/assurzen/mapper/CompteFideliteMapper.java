package ci.assurzen.mapper;

import ci.assurzen.dto.CompteFideliteResponse;
import ci.assurzen.entities.CompteFidelite;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = MappingSupport.class)
public interface CompteFideliteMapper {

    @Mapping(target = "insuredId", source = "insured.id")
    @Mapping(target = "insuredName", source = "insured", qualifiedByName = "insuredDisplayName")
    @Mapping(target = "palierId", source = "palier.id")
    @Mapping(target = "palierLibelle", source = "palier.libelle")
    @Mapping(target = "gestionnaireDedieId", source = "gestionnaireDedie.id")
    @Mapping(target = "gestionnaireDedieNom", source = "gestionnaireDedie", qualifiedByName = "userDisplayName")
    CompteFideliteResponse toResponse(CompteFidelite compte);
}
