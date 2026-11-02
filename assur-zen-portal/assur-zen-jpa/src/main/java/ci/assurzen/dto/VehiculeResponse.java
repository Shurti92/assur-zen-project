package ci.assurzen.dto;

import ci.assurzen.entities.VehiculeStatut;
import ci.assurzen.entities.VehiculeType;

import java.time.Instant;
import java.time.LocalDate;

public record VehiculeResponse(
        Long id,
        String immatriculation,
        VehiculeType type,
        String marque,
        String modele,
        String numeroChassis,
        LocalDate dateMiseEnCirculation,
        Integer puissanceFiscale,
        Integer cylindree,
        String couleur,
        Long proprietaireId,
        String proprietaireName,
        VehiculeStatut statut,
        Long currentFleetId,
        String currentFleetNumber,
        Instant createdAt,
        Instant updatedAt
) {
}
