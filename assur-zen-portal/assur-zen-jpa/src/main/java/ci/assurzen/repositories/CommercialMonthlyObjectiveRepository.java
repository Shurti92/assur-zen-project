package ci.assurzen.repositories;

import ci.assurzen.entities.CommercialMonthlyObjective;
import ci.assurzen.entities.CommercialObjectiveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommercialMonthlyObjectiveRepository extends JpaRepository<CommercialMonthlyObjective, Long> {

    List<CommercialMonthlyObjective> findByCommercialIdOrderByMoisComptable_NumeroDesc(Long commercialId);

    Optional<CommercialMonthlyObjective> findByCommercialIdAndMoisComptableIdAndObjectiveType(
            Long commercialId, Long moisComptableId, CommercialObjectiveType objectiveType);
}
