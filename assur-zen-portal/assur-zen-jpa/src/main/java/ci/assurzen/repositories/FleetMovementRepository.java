package ci.assurzen.repositories;

import ci.assurzen.entities.FleetMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FleetMovementRepository extends JpaRepository<FleetMovement, Long> {

    List<FleetMovement> findByFleetIdOrderByCreatedAtDesc(Long fleetId);
}
