package ci.assurzen.mapper;

import ci.assurzen.dto.FidelisationHistoriqueResponse;
import ci.assurzen.entities.FidelisationHistorique;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = MappingSupport.class)
public interface FidelisationHistoriqueMapper {

    @Mapping(target = "compteFideliteId", source = "compteFidelite.id")
    @Mapping(target = "contratId", source = "contrat.id")
    @Mapping(target = "contratPolicyNumber", source = "contrat.policyNumber")
    @Mapping(target = "auteurId", source = "auteur.id")
    @Mapping(target = "auteur", source = "auteur", qualifiedByName = "userDisplayName")
    FidelisationHistoriqueResponse toResponse(FidelisationHistorique historique);
}
