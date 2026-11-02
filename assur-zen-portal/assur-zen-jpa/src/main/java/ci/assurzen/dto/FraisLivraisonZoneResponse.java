package ci.assurzen.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FraisLivraisonZoneResponse(
        Long id,
        String code,
        String libelle,
        BigDecimal montant,
        Boolean actif,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {
}
