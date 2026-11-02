package ci.assurzen.dto;

import ci.assurzen.entities.FleetStatus;
import ci.assurzen.entities.FleetType;

import java.time.Instant;
import java.time.LocalDate;

public record FleetResponse(
        Long id,
        String fleetNumber,
        Long clientId,
        String clientName,
        String name,
        String description,
        FleetType fleetType,
        Long produitId,
        String produitNom,
        FleetStatus status,
        LocalDate startDate,
        LocalDate endDate,
        String managerName,
        String managerPhone,
        String managerEmail,
        Integer totalVehicles,
        Integer activeVehicles,
        Instant createdAt,
        Instant updatedAt
) {
}
