package ci.assurzen.repositories;

import ci.assurzen.entities.AuditFlux;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditFluxRepository extends JpaRepository<AuditFlux, Long> {
}
