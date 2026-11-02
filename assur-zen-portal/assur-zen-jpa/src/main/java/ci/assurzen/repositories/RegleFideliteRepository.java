package ci.assurzen.repositories;

import ci.assurzen.entities.RegleFidelite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegleFideliteRepository extends JpaRepository<RegleFidelite, Long> {

    Optional<RegleFidelite> findByCode(String code);

    List<RegleFidelite> findByActifTrueOrderByOrdreAsc();
}
