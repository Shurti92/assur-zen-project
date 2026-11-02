package ci.assurzen.repositories;

import ci.assurzen.entities.Vehicule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VehiculeRepository extends JpaRepository<Vehicule, Long> {

    Optional<Vehicule> findByImmatriculation(String immatriculation);

    Optional<Vehicule> findByNumeroChassis(String numeroChassis);

    // Verrou pessimiste : voir FleetService.lockVehicule (EntityManager.find direct, pas une requete
    // JPQL @Lock ici - incompatible avec MariaDB, cf. le commentaire de cette methode).
}
