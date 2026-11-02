package ci.assurzen.dto;

import ci.assurzen.entities.FleetMovementType;

import java.time.Instant;
import java.time.LocalDate;

public record FleetMovementResponse(
        Long id,
        Long fleetId,
        Long vehiculeId,
        String immatriculation,
        FleetMovementType movementType,
        LocalDate movementDate,
        String reason,
        Long oldVehiculeId,
        String oldImmatriculation,
        Long newVehiculeId,
        String newImmatriculation,
        Long sourceFleetId,
        String sourceFleetNumber,
        Long destinationFleetId,
        String destinationFleetNumber,
        Long contractId,
        String contractPolicyNumber,
        Long performedById,
        String performedByName,
        Instant createdAt
) {
}
