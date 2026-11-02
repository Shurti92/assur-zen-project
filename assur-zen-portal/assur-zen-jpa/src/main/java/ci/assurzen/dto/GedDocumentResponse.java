package ci.assurzen.dto;

import ci.assurzen.entities.GedConfidentialityLevel;
import ci.assurzen.entities.GedDocumentStatus;

import java.time.Instant;

public record GedDocumentResponse(
        Long id,
        String uuid,
        String documentNumber,
        Long documentTypeId,
        String documentTypeCode,
        String documentTypeNom,
        Long categoryId,
        String categoryCode,
        String categoryNom,
        String title,
        String description,
        GedDocumentStatus status,
        GedConfidentialityLevel confidentialityLevel,
        String source,
        String rejectionReason,
        String checksumSha256,
        Integer currentVersionNumber,
        Long duplicateOfDocumentId,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        Instant archivedAt,
        String archivedBy
) {
}
