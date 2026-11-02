package ci.assurzen.repositories;

import ci.assurzen.entities.FleetVehicle;
import ci.assurzen.entities.FleetVehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FleetVehicleRepository extends JpaRepository<FleetVehicle, Long> {

    List<FleetVehicle> findByFleetIdOrderByCreatedAtDesc(Long fleetId);

    List<FleetVehicle> findByFleetIdAndStatusOrderByCreatedAtDesc(Long fleetId, FleetVehicleStatus status);

    Optional<FleetVehicle> findByFleetIdAndVehiculeIdAndStatus(Long fleetId, Long vehiculeId, FleetVehicleStatus status);

    long countByFleetIdAndStatus(Long fleetId, FleetVehicleStatus status);

    @Query("select count(distinct fv.vehicule.id) from FleetVehicle fv where fv.fleet.id = :fleetId")
    long countDistinctVehiculeIdByFleetId(@Param("fleetId") Long fleetId);

    List<FleetVehicle> findByVehiculeIdOrderByCreatedAtDesc(Long vehiculeId);
}
