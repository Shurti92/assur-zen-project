package ci.assurzen.dto;

import ci.assurzen.entities.StatutParrainage;
import ci.assurzen.entities.TypeEvenementParrainage;

import java.time.Instant;

public record HistoriqueParrainageResponse(
        Long id,
        Long parrainageId,
        TypeEvenementParrainage typeEvenement,
        StatutParrainage ancienStatut,
        StatutParrainage nouveauStatut,
        String description,
        Long auteurId,
        String auteurNom,
        Instant createdAt
) {
}
