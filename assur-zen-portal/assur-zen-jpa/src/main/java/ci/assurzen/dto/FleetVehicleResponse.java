package ci.assurzen.dto;

import ci.assurzen.entities.FleetVehicleStatus;
import ci.assurzen.entities.VehiculeType;

import java.time.LocalDate;

public record FleetVehicleResponse(
        Long id,
        Long fleetId,
        String fleetNumber,
        Long vehiculeId,
        String immatriculation,
        VehiculeType vehiculeType,
        FleetVehicleStatus status,
        LocalDate dateAdded,
        LocalDate dateRemoved,
        /** Present uniquement sur la reponse de addVehicle() (SMS d'incorporation au gestionnaire/client) -
         * null sur les autres operations (retrait, remplacement, transfert, liste), meme convention que
         * CommercialResponse.notificationEnvoyee. */
        Boolean notificationEnvoyee
) {
}
