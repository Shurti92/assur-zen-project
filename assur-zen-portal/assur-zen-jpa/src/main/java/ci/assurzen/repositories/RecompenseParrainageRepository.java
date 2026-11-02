package ci.assurzen.repositories;

import ci.assurzen.entities.RecompenseParrainage;
import ci.assurzen.entities.StatutRecompenseParrainage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecompenseParrainageRepository extends JpaRepository<RecompenseParrainage, Long> {

    List<RecompenseParrainage> findByParrainageId(Long parrainageId);

    List<RecompenseParrainage> findByBeneficiaireId(Long beneficiaireId);

    List<RecompenseParrainage> findByStatut(StatutRecompenseParrainage statut);

    List<RecompenseParrainage> findByBeneficiaireIdAndStatutIn(Long beneficiaireId, List<StatutRecompenseParrainage> statuts);
}
