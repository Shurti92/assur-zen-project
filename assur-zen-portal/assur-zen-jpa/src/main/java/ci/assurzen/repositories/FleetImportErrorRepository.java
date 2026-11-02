package ci.assurzen.repositories;

import ci.assurzen.entities.FleetImportError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FleetImportErrorRepository extends JpaRepository<FleetImportError, Long> {

    List<FleetImportError> findByFleetImportIdOrderByLigneNumeroAsc(Long fleetImportId);
}
