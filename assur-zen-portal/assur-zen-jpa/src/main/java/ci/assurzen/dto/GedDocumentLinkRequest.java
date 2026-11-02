package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GedDocumentLinkRequest(
        @NotBlank @Size(max = 50) String entityType,
        @NotNull Long entityId,
        @Size(max = 50) String relationshipType
) {
}
