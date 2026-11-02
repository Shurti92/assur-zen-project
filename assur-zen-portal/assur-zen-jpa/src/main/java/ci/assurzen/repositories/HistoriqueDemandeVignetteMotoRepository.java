package ci.assurzen.repositories;

import ci.assurzen.entities.HistoriqueDemandeVignetteMoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoriqueDemandeVignetteMotoRepository extends JpaRepository<HistoriqueDemandeVignetteMoto, Long> {

    List<HistoriqueDemandeVignetteMoto> findByDemandeVignetteMotoIdOrderByCreatedAtAsc(Long demandeVignetteMotoId);
}
