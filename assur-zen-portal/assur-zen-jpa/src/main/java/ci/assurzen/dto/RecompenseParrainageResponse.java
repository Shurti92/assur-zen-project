package ci.assurzen.dto;

import ci.assurzen.entities.StatutRecompenseParrainage;
import ci.assurzen.entities.TypeRecompenseParrainage;

import java.time.Instant;

public record RecompenseParrainageResponse(
        Long id,
        Long parrainageId,
        String parrainageReference,
        Long regleId,
        String regleNom,
        Long beneficiaireId,
        String beneficiaireNom,
        TypeRecompenseParrainage typeRecompense,
        StatutRecompenseParrainage statut,
        Long montant,
        String devise,
        Double pourcentage,
        Integer points,
        String description,
        Instant dateEligibilite,
        Instant dateApprobation,
        Instant dateAttribution,
        Instant dateAnnulation,
        Instant dateExpiration,
        String motifAnnulation,
        Instant createdAt
) {
}
