package ci.assurzen.dto;

import ci.assurzen.entities.TypeAvenant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AvenantRequest(
        @NotNull TypeAvenant type,
        @NotNull LocalDate dateEffet,
        @NotBlank @Size(max = 500) String motif,
        String description,
        /** Facultatifs - uniquement pour les avenants qui touchent un champ deja modelise sur Contract
         * (prime, commission, partenaire, compagnie). Null = champ inchange. Les autres types
         * (garanties, bien assure, conducteur, zone, franchise, plafond...) ne correspondent a aucun
         * champ structure du schema actuel et restent traces via motif/description uniquement. */
        Long premium,
        BigDecimal commissionRate,
        Long commissionAmount,
        Long partenaireId,
        Long compagnieId,
        /** Commission partenaire explicite (FCFA entiers). Fournie = appliquee telle quelle ; null =
         * recalcul (premium - commissionAmount) si prime/commission/partenaire changent. */
        Long partnerShare
) {
}
