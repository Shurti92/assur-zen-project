package ci.assurzen.dto;

import ci.assurzen.entities.FleetImportFormat;
import ci.assurzen.entities.FleetImportStatus;

import java.time.Instant;

public record FleetImportResponse(
        Long id,
        Long fleetId,
        String fileName,
        FleetImportFormat format,
        FleetImportStatus status,
        Integer totalRows,
        Integer validRows,
        Integer invalidRows,
        Integer duplicateRows,
        Integer successCount,
        Integer errorCount,
        String uploadedBy,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        /** Present uniquement sur la reponse de confirm() (SMS d'incorporation agrege au gestionnaire/
         * client) - null sur preview()/list()/get(), meme convention que FleetVehicleResponse.notificationEnvoyee. */
        Boolean notificationEnvoyee
) {
}
