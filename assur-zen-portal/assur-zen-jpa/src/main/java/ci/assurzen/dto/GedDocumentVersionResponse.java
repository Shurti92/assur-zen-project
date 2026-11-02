package ci.assurzen.dto;

import ci.assurzen.entities.GedStorageProvider;

import java.time.Instant;

public record GedDocumentVersionResponse(
        Long id,
        Integer versionNumber,
        String fileName,
        String originalFileName,
        String mimeType,
        String extension,
        Long fileSizeBytes,
        String checksumSha256,
        GedStorageProvider storageProvider,
        Instant createdAt,
        String createdBy
) {
}
