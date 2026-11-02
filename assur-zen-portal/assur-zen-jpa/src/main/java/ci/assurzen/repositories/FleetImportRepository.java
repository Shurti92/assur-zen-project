package ci.assurzen.repositories;

import ci.assurzen.entities.FleetImport;
import ci.assurzen.entities.FleetImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FleetImportRepository extends JpaRepository<FleetImport, Long> {

    List<FleetImport> findByFleetIdOrderByCreatedAtDesc(Long fleetId);

    Optional<FleetImport> findByFleetIdAndFileChecksumAndStatusIn(Long fleetId, String fileChecksum, List<FleetImportStatus> statuses);
}
