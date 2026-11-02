package ci.assurzen.repositories;

import ci.assurzen.entities.SatisfactionImportMappingValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SatisfactionImportMappingValueRepository extends JpaRepository<SatisfactionImportMappingValue, Long> {

    List<SatisfactionImportMappingValue> findByMappingId(Long mappingId);

    Optional<SatisfactionImportMappingValue> findByIdAndMappingId(Long id, Long mappingId);
}
