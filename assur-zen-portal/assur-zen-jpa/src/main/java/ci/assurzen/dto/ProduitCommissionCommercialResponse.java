package ci.assurzen.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProduitCommissionCommercialResponse(
        Long id,
        Long produitId,
        String produitName,
        BigDecimal tauxCommission,
        String libTauxCommission,
        boolean active,
        Instant creation,
        Instant lastUpdate
) {
}
