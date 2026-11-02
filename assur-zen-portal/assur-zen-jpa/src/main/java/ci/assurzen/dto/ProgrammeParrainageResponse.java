package ci.assurzen.dto;

import ci.assurzen.entities.StatutProgrammeParrainage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public record ProgrammeParrainageResponse(
        Long id,
        String code,
        String nom,
        String description,
        StatutProgrammeParrainage statut,
        LocalDate dateDebut,
        LocalDate dateFin,
        Integer maxParrainagesParClient,
        Integer maxRecompensesParClient,
        Integer delaiCarenceJours,
        Integer dureeValiditeJours,
        Long primeMinimum,
        Set<Long> produitsEligibles,
        Instant createdAt,
        Instant lastUpdate
) {
}
