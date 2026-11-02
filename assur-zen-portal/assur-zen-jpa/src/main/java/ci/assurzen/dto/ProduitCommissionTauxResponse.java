package ci.assurzen.dto;

import ci.assurzen.entities.PartenaireType;

import java.math.BigDecimal;
import java.time.Instant;

public record ProduitCommissionTauxResponse(
        Long id,
        Long produitId,
        String produitName,
        PartenaireType typePartenaire,
        BigDecimal tauxCommission,
        String libTauxCommission,
        boolean active,
        Instant creation,
        Instant lastUpdate
) {
}
