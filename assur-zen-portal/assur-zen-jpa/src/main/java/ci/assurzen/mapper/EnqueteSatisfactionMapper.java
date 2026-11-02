package ci.assurzen.mapper;

import ci.assurzen.dto.EnqueteSatisfactionResponse;
import ci.assurzen.entities.EnqueteSatisfaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = MappingSupport.class)
public interface EnqueteSatisfactionMapper {

    @Mapping(target = "insuredId", source = "insured.id")
    @Mapping(target = "insuredName", source = "insured", qualifiedByName = "insuredDisplayName")
    @Mapping(target = "contratId", source = "contrat.id")
    @Mapping(target = "contratPolicyNumber", source = "contrat.policyNumber")
    @Mapping(target = "sinistreId", source = "sinistre.id")
    @Mapping(target = "sinistreNumero", source = "sinistre.numero")
    @Mapping(target = "categorieNps", source = "scoreNps", qualifiedByName = "categorieNps")
    EnqueteSatisfactionResponse toResponse(EnqueteSatisfaction enquete);
}
