package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SinistreRequest(
        @NotNull Long contractId,
        @NotBlank @Size(max = 200) String nature,
        @NotNull LocalDate dateSurvenance,
        Long montantEstime,
        Long assigneeId,
        Long declarantId,
        Long compagnieId,
        @Size(max = 200) String gestionnaireNom,
        @Size(max = 4000) String description,
        String notes
) {
}
