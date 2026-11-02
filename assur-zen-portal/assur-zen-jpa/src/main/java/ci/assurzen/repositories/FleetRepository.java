package ci.assurzen.repositories;

import ci.assurzen.entities.Fleet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FleetRepository extends JpaRepository<Fleet, Long> {

    Optional<Fleet> findByFleetNumber(String fleetNumber);
}
