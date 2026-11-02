package ci.assurzen.repositories;

import ci.assurzen.entities.PrimeObjectif;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrimeObjectifRepository extends JpaRepository<PrimeObjectif, Long> {

    Optional<PrimeObjectif> findByPartenaireIdAndExerciceComptableId(Long partenaireId, Long exerciceComptableId);

    List<PrimeObjectif> findByPartenaireId(Long partenaireId, Sort sort);
}
