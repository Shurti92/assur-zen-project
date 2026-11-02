package ci.assurzen.repositories;

import ci.assurzen.entities.FidelisationHistorique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FidelisationHistoriqueRepository extends JpaRepository<FidelisationHistorique, Long> {

    List<FidelisationHistorique> findByCompteFideliteIdOrderByCreatedAtDesc(Long compteFideliteId);
}
