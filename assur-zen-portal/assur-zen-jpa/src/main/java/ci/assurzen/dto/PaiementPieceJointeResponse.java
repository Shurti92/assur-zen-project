package ci.assurzen.dto;

import java.time.Instant;

public record PaiementPieceJointeResponse(
        Long id,
        Long paiementId,
        String fileName,
        String contentType,
        Long sizeBytes,
        String uploadedBy,
        Instant createdAt
) {
}
