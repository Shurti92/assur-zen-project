package ci.assurzen.repositories;

import ci.assurzen.entities.Parrainage;
import ci.assurzen.entities.StatutParrainage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParrainageRepository extends JpaRepository<Parrainage, Long> {

    Optional<Parrainage> findByReference(String reference);

    List<Parrainage> findByParrainId(Long parrainId);

    List<Parrainage> findByParrainIdAndProgrammeId(Long parrainId, Long programmeId);

    List<Parrainage> findByParrainIdAndProgrammeIdAndStatutIn(Long parrainId, Long programmeId, List<StatutParrainage> statuts);

    List<Parrainage> findByFilleulId(Long filleulId);

    List<Parrainage> findByFilleulEmailIgnoreCaseOrFilleulTelephone(String filleulEmail, String filleulTelephone);

    List<Parrainage> findByStatut(StatutParrainage statut);
}
