package ci.assurzen.dto;

import ci.assurzen.entities.CategorieAgeMoto;
import ci.assurzen.entities.CategorieCylindree;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TarifVignetteMotoRequest(
        @NotNull CategorieAgeMoto categorieAge,
        @NotNull CategorieCylindree categorieCylindree,
        @NotNull @Positive BigDecimal montantVignette,
        @NotNull @PositiveOrZero BigDecimal fraisOperation,
        @NotNull LocalDate dateDebutValidite,
        LocalDate dateFinValidite
) {
}
