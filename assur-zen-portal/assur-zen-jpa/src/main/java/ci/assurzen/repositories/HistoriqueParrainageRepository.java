package ci.assurzen.repositories;

import ci.assurzen.entities.HistoriqueParrainage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoriqueParrainageRepository extends JpaRepository<HistoriqueParrainage, Long> {

    List<HistoriqueParrainage> findByParrainageIdOrderByCreatedAtAsc(Long parrainageId);
}
