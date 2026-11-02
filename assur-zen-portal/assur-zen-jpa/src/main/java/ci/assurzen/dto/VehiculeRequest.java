package ci.assurzen.dto;

import ci.assurzen.entities.VehiculeStatut;
import ci.assurzen.entities.VehiculeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VehiculeRequest(
        @Size(max = 20) String immatriculation,
        @NotNull VehiculeType type,
        @NotBlank @Size(max = 60) String marque,
        @NotBlank @Size(max = 60) String modele,
        @Size(max = 50) String numeroChassis,
        LocalDate dateMiseEnCirculation,
        Integer puissanceFiscale,
        @Positive Integer cylindree,
        @Size(max = 30) String couleur,
        @NotNull Long proprietaireId,
        VehiculeStatut statut
) {
}
