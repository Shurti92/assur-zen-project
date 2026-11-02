package ci.assurzen.dto;

import ci.assurzen.entities.FrequencePaiementCommission;
import ci.assurzen.entities.PartenaireType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PartenaireRequest(
        @Size(max = 20)
        String codePartenaire,

        @NotNull
        PartenaireType type,

        @NotBlank
        @Size(max = 50)
        String nom,

        @NotBlank
        @Size(max = 200)
        String prenoms,

        @DecimalMin(value = "0.00")
        @Digits(integer = 13, fraction = 2)
        BigDecimal objectifProduction,

        Long profileId,

        Long exerciceComptableId,

        Boolean active,

        Long villePartenaireId,

        Long typeProfileId,

        @Size(max = 20)
        String telephone,

        @Size(max = 20)
        String numeroWaveCommission,

        /** Absent a la creation => MENSUELLE. Absent a la mise a jour => valeur existante preservee. */
        FrequencePaiementCommission frequencePaiementCommission,

        /** Absent a la creation => false. Absent a la mise a jour => valeur existante preservee (ne
         * declenche pas de desactivation silencieuse d'un compte deja actif). */
        Boolean accesActiviteCommerciale,

        /** Absent a la creation => false. Absent a la mise a jour => valeur existante preservee. */
        Boolean primeObjectifActivee
) {
}
