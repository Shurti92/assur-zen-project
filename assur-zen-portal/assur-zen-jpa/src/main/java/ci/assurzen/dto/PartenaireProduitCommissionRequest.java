package ci.assurzen.dto;

import ci.assurzen.entities.BeneficiaireCommission;
import ci.assurzen.entities.PartenaireType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * PARTENAIRE : partenaireId OU typePartenaire (l'un des deux, pas les deux), commercialId null.
 * COMMERCIAL : commercialId obligatoire (id User du commercial), partenaireId/typePartenaire null.
 * montant en FCFA entiers. dateDebut/dateFin facultatives. active null = true.
 */
public record PartenaireProduitCommissionRequest(
        @NotNull Long produitId,
        @NotNull BeneficiaireCommission beneficiaire,
        Long partenaireId,
        PartenaireType typePartenaire,
        Long commercialId,
        @NotNull @Min(0) Long montant,
        Boolean active,
        LocalDate dateDebut,
        LocalDate dateFin
) {
}
