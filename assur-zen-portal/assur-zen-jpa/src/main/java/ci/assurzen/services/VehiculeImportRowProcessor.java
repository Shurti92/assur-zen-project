package ci.assurzen.services;

import ci.assurzen.entities.Fleet;
import ci.assurzen.entities.Vehicule;
import ci.assurzen.repositories.VehiculeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Traite UNE ligne d'import de vehicule dans sa PROPRE transaction (REQUIRES_NEW), classe separee
 * de VehiculeImportService de facon deliberee : un appel self-invoque (this.methode()) depuis une
 * autre methode de la meme classe ne passe pas par le proxy Spring et @Transactional n'aurait alors
 * aucun effet. Meme lecon que CsvImportRowProcessor (import Satisfaction) : sans REQUIRES_NEW,
 * l'echec d'une seule ligne (contrainte DB, regle metier) marquerait la transaction englobante
 * "rollback-only" et annulerait silencieusement tout l'import.
 */
@Service
@RequiredArgsConstructor
class VehiculeImportRowProcessor {

    private final FleetService fleetService;
    private final VehiculeRepository vehiculeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importerLigne(Long fleetId, LigneVehiculeValidee ligne) {
        Fleet fleet = fleetService.resolveFleet(fleetId);
        Vehicule vehicule = trouverVehiculeExistant(ligne)
                .orElseGet(() -> vehiculeRepository.save(Vehicule.builder()
                        .immatriculation(ligne.immatriculation())
                        .type(ligne.type())
                        .marque(ligne.marque())
                        .modele(ligne.modele())
                        .numeroChassis(ligne.numeroChassis())
                        .dateMiseEnCirculation(ligne.dateMiseEnCirculation())
                        .puissanceFiscale(ligne.puissanceFiscale())
                        .cylindree(ligne.cylindree())
                        .couleur(ligne.couleur())
                        .proprietaire(fleet.getClient())
                        .statut(ligne.statut())
                        .build()));

        fleetService.ajouterVehiculeAuCoeur(fleet, vehicule, "Import en masse");
    }

    /** Immatriculation optionnelle (2026-09-13) : findByImmatriculation(null) matcherait N'IMPORTE
     * QUEL vehicule existant sans plaque (WHERE immatriculation IS NULL), pas forcement celui de
     * cette ligne - dangereux (rattacherait la ligne au mauvais vehicule). Repli sur le numero de
     * chassis quand l'immatriculation est absente ; si les deux sont absents, toujours creer un
     * nouveau vehicule plutot que de risquer une correspondance fausse. */
    private Optional<Vehicule> trouverVehiculeExistant(LigneVehiculeValidee ligne) {
        if (StringUtils.hasText(ligne.immatriculation())) {
            return vehiculeRepository.findByImmatriculation(ligne.immatriculation());
        }
        if (StringUtils.hasText(ligne.numeroChassis())) {
            return vehiculeRepository.findByNumeroChassis(ligne.numeroChassis());
        }
        return Optional.empty();
    }
}
