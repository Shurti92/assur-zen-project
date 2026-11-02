package ci.assurzen.mapper;

import ci.assurzen.dto.CampagneDestinataireResponse;
import ci.assurzen.entities.CampagneDestinataire;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = MappingSupport.class)
public interface CampagneDestinataireMapper {

    @Mapping(target = "campagneId", source = "campagne.id")
    @Mapping(target = "insuredId", source = "insured.id")
    @Mapping(target = "insuredName", source = "insured", qualifiedByName = "insuredDisplayName")
    CampagneDestinataireResponse toResponse(CampagneDestinataire destinataire);
}
