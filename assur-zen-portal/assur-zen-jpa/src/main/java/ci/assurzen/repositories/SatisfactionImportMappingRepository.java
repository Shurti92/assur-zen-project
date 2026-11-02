package ci.assurzen.repositories;

import ci.assurzen.entities.SatisfactionImportMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SatisfactionImportMappingRepository extends JpaRepository<SatisfactionImportMapping, Long> {

    List<SatisfactionImportMapping> findByProfileIdOrderByOrdreAsc(Long profileId);

    Optional<SatisfactionImportMapping> findByIdAndProfileId(Long id, Long profileId);
}
