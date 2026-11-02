package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record ProgrammeParrainageRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 4000) String description,
        LocalDate dateDebut,
        LocalDate dateFin,
        Integer maxParrainagesParClient,
        Integer maxRecompensesParClient,
        Integer delaiCarenceJours,
        Integer dureeValiditeJours,
        Long primeMinimum,
        Set<Long> produitsEligibles
) {
}
