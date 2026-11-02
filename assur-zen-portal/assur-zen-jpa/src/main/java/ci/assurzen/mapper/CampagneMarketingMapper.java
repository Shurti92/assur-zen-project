package ci.assurzen.mapper;

import ci.assurzen.dto.CampagneMarketingResponse;
import ci.assurzen.entities.CampagneMarketing;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = MappingSupport.class)
public interface CampagneMarketingMapper {

    @Mapping(target = "createdById", source = "campagne.createdBy.id")
    @Mapping(target = "createdBy", source = "campagne.createdBy", qualifiedByName = "userDisplayName")
    @Mapping(target = "segmentId", source = "campagne.segment.id")
    @Mapping(target = "segmentNom", source = "campagne.segment.nom")
    @Mapping(target = "templateId", source = "campagne.template.id")
    @Mapping(target = "templateNom", source = "campagne.template.nom")
    CampagneMarketingResponse toResponse(CampagneMarketing campagne, long nbDestinataires, long nbEnvoyes, long nbEchecs);
}
