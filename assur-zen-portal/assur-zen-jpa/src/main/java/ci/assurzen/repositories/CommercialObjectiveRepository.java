package ci.assurzen.repositories;

import ci.assurzen.entities.CommercialObjective;
import ci.assurzen.entities.CommercialObjectiveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommercialObjectiveRepository extends JpaRepository<CommercialObjective, Long> {

    List<CommercialObjective> findByCommercialIdOrderByExerciceComptableIdDesc(Long commercialId);

    Optional<CommercialObjective> findByCommercialIdAndExerciceComptableIdAndObjectiveType(
            Long commercialId, Long exerciceComptableId, CommercialObjectiveType objectiveType);
}
