package ci.assurzen.repositories;

import ci.assurzen.entities.CodeParrainage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeParrainageRepository extends JpaRepository<CodeParrainage, Long> {

    Optional<CodeParrainage> findByCode(String code);

    List<CodeParrainage> findByParrainIdAndProgrammeId(Long parrainId, Long programmeId);

    List<CodeParrainage> findByParrainId(Long parrainId);
}
