package ci.assurzen.services;

import ci.assurzen.entities.VehiculeStatut;
import ci.assurzen.entities.VehiculeType;

import java.time.LocalDate;

/** Ligne d'import de vehicule ayant passe toutes les validations de format, prete a etre ecrite en
 * base lors de la confirmation. */
record LigneVehiculeValidee(
        int ligneNumero,
        String immatriculation,
        VehiculeType type,
        String marque,
        String modele,
        String numeroChassis,
        LocalDate dateMiseEnCirculation,
        Integer puissanceFiscale,
        Integer cylindree,
        String couleur,
        VehiculeStatut statut
) {
}
