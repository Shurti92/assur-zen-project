package ci.assurzen.mapper;

import ci.assurzen.dto.CommunicationTemplateResponse;
import ci.assurzen.entities.CommunicationTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = MappingSupport.class)
public interface CommunicationTemplateMapper {

    @Mapping(target = "categorieId", source = "categorie.id")
    @Mapping(target = "categorieLibelle", source = "categorie.libelle")
    @Mapping(target = "langueId", source = "langue.id")
    @Mapping(target = "langueLibelle", source = "langue.libelle")
    @Mapping(target = "variableCodes", source = "variables", qualifiedByName = "variableCodes")
    CommunicationTemplateResponse toResponse(CommunicationTemplate template);
}
