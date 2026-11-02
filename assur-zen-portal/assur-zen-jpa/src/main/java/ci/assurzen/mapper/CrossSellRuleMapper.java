package ci.assurzen.mapper;

import ci.assurzen.dto.CrossSellRuleResponse;
import ci.assurzen.entities.CrossSellRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CrossSellRuleMapper {

    @Mapping(target = "produitDeclencheurId", source = "produitDeclencheur.id")
    @Mapping(target = "produitDeclencheurNom", source = "produitDeclencheur.nomProduit")
    @Mapping(target = "produitRecommandeId", source = "produitRecommande.id")
    @Mapping(target = "produitRecommandeNom", source = "produitRecommande.nomProduit")
    CrossSellRuleResponse toResponse(CrossSellRule regle);
}
