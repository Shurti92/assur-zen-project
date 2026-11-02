package ci.assurzen.repositories;

import ci.assurzen.entities.RegleRecompenseParrainage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegleRecompenseParrainageRepository extends JpaRepository<RegleRecompenseParrainage, Long> {

    List<RegleRecompenseParrainage> findByProgrammeIdOrderByPrioriteAsc(Long programmeId);

    List<RegleRecompenseParrainage> findByProgrammeIdAndActifTrueOrderByPrioriteAsc(Long programmeId);
}
