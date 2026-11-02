package ci.assurzen.repositories;

import ci.assurzen.entities.MoisComptable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MoisComptableRepository extends JpaRepository<MoisComptable, Long> {

    List<MoisComptable> findByExerciceComptableIdOrderByNumeroAsc(Long exerciceComptableId);

    boolean existsByExerciceComptableId(Long exerciceComptableId);

    List<MoisComptable> findByClotureFalseAndDateFinBefore(LocalDate date);

    List<MoisComptable> findByExerciceComptableIdAndClotureFalse(Long exerciceComptableId);

    /** Mois comptable "courant" (celui dont la periode couvre la date donnee), toutes exercices confondus - utilise par le dashboard commercial (§34). */
    Optional<MoisComptable> findFirstByDateDebutLessThanEqualAndDateFinGreaterThanEqualOrderByDateDebutDesc(LocalDate dateDebutMax, LocalDate dateFinMin);

    /** Mois deja commences (dateDebut &lt;= date), du plus recent au plus ancien - historique de production du dashboard. */
    List<MoisComptable> findByDateDebutLessThanEqualOrderByDateDebutDesc(LocalDate date);

    long countByExerciceComptableId(Long exerciceComptableId);

    /** Mois clotures n'ayant pas encore de CommercialPerformance calculee - utilise par CommercialPerformanceScheduler. */
    List<MoisComptable> findByClotureTrue();
}
