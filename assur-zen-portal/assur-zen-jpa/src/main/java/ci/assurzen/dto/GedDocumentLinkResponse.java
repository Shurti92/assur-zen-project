package ci.assurzen.dto;

import java.time.Instant;

public record GedDocumentLinkResponse(
        Long id,
        String entityType,
        Long entityId,
        String relationshipType,
        Instant createdAt,
        String createdBy
) {
}
