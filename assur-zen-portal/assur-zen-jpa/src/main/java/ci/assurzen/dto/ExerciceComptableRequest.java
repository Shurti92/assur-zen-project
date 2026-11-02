package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExerciceComptableRequest(
        @NotBlank @Size(max = 100) String libelle,
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin,
        Boolean active,
        BigDecimal objectifGlobal
) {
}
