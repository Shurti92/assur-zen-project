package ci.assurzen.repositories;

import ci.assurzen.entities.HistoriqueModification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoriqueModificationRepository extends JpaRepository<HistoriqueModification, Long> {

    List<HistoriqueModification> findByEntityNameAndEntityIdOrderByCreatedAtDesc(String entityName, String entityId);

    List<HistoriqueModification> findByEntityNameAndEntityIdInOrderByCreatedAtDesc(String entityName, List<String> entityIds);

    List<HistoriqueModification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<HistoriqueModification> findByCorrelationIdOrderByCreatedAtAsc(String correlationId);
}
