package ci.assurzen.dto;

import java.time.Instant;

public record SinistrePhotoResponse(
        Long id,
        Long sinistreId,
        String fileName,
        String contentType,
        Long sizeBytes,
        String uploadedBy,
        Instant createdAt
) {
}
