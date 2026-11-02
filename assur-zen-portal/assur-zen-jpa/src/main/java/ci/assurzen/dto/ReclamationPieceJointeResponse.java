package ci.assurzen.dto;

import java.time.Instant;

public record ReclamationPieceJointeResponse(
        Long id,
        Long reclamationId,
        String fileName,
        String contentType,
        Long sizeBytes,
        String uploadedBy,
        Instant createdAt
) {
}
