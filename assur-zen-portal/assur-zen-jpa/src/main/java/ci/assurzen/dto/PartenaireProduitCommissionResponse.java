package ci.assurzen.dto;

import ci.assurzen.entities.BeneficiaireCommission;
import ci.assurzen.entities.PartenaireType;

import java.time.Instant;
import java.time.LocalDate;

public record PartenaireProduitCommissionResponse(
        Long id,
        Long produitId,
        String produitName,
        BeneficiaireCommission beneficiaire,
        Long partenaireId,
        String partenaireNom,
        PartenaireType typePartenaire,
        Long commercialId,
        String commercialUsername,
        Long montant,
        boolean active,
        LocalDate dateDebut,
        LocalDate dateFin,
        Instant creation,
        Instant lastUpdate
) {
}
