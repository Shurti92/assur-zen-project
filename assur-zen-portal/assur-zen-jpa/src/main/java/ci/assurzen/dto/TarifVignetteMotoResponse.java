package ci.assurzen.dto;

import ci.assurzen.entities.CategorieAgeMoto;
import ci.assurzen.entities.CategorieCylindree;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TarifVignetteMotoResponse(
        Long id,
        CategorieAgeMoto categorieAge,
        CategorieCylindree categorieCylindree,
        BigDecimal montantVignette,
        BigDecimal fraisOperation,
        LocalDate dateDebutValidite,
        LocalDate dateFinValidite,
        Boolean actif,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {
}
