package ci.assurzen.dto;

import ci.assurzen.entities.BeneficiaireParrainage;
import ci.assurzen.entities.TypeRecompenseParrainage;

public record RegleRecompenseParrainageResponse(
        Long id,
        Long programmeId,
        String programmeNom,
        String nom,
        String description,
        TypeRecompenseParrainage typeRecompense,
        BeneficiaireParrainage beneficiaire,
        Long montantFixe,
        Double pourcentage,
        Integer points,
        Long primeMinimum,
        Long plafondRecompense,
        Integer priorite,
        boolean actif
) {
}
