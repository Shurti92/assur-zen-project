package ci.assurzen.mapper;

import ci.assurzen.dto.RecompenseEchangeResponse;
import ci.assurzen.entities.RecompenseEchange;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = MappingSupport.class)
public interface RecompenseEchangeMapper {

    @Mapping(target = "compteFideliteId", source = "compteFidelite.id")
    @Mapping(target = "insuredId", source = "compteFidelite.insured.id")
    @Mapping(target = "insuredName", source = "compteFidelite.insured", qualifiedByName = "insuredDisplayName")
    @Mapping(target = "recompenseId", source = "recompense.id")
    @Mapping(target = "recompenseNom", source = "recompense.nom")
    @Mapping(target = "traiteParId", source = "traitePar.id")
    @Mapping(target = "traitePar", source = "traitePar", qualifiedByName = "userDisplayName")
    RecompenseEchangeResponse toResponse(RecompenseEchange echange);
}
