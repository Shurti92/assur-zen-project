package ci.assurzen.repositories;

import ci.assurzen.entities.ProgrammeParrainage;
import ci.assurzen.entities.StatutProgrammeParrainage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgrammeParrainageRepository extends JpaRepository<ProgrammeParrainage, Long> {

    Optional<ProgrammeParrainage> findByCode(String code);

    List<ProgrammeParrainage> findByStatut(StatutProgrammeParrainage statut);
}
