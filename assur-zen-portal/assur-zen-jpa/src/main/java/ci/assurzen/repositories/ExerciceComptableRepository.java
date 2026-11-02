package ci.assurzen.repositories;

import ci.assurzen.entities.ExerciceComptable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciceComptableRepository extends JpaRepository<ExerciceComptable, Long> {

    Optional<ExerciceComptable> findByLibelle(String libelle);

    List<ExerciceComptable> findByClotureFalseAndDateFinBefore(LocalDate date);

    List<ExerciceComptable> findByActiveTrue(Sort sort);

    List<ExerciceComptable> findByDateDebutLessThanEqualAndDateFinGreaterThanEqual(LocalDate dateDebutMax, LocalDate dateFinMin);
}
