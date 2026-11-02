package ci.assurzen.dto;

import java.time.Instant;

public record ProduitResponse(
        Long id,
        String nomProduit,
        boolean active,
        Long productTypeId,
        String productTypeCode,
        String productTypeNom,
        Instant creation,
        Instant lastUpdate
) {
}
