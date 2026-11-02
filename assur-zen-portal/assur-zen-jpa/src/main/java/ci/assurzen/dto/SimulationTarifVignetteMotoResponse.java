package ci.assurzen.dto;

import ci.assurzen.entities.CategorieAgeMoto;
import ci.assurzen.entities.CategorieCylindree;

import java.math.BigDecimal;

public record SimulationTarifVignetteMotoResponse(
        CategorieAgeMoto categorieAge,
        Integer ageMoto,
        CategorieCylindree categorieCylindree,
        BigDecimal montantVignette,
        BigDecimal fraisOperation,
        BigDecimal fraisLivraison,
        BigDecimal fraisAccessoires,
        BigDecimal montantTotal,
        BigDecimal reductionApporteur
) {
}
