package ci.assurzen.repositories;

import ci.assurzen.entities.TraductionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TraductionEntryRepository extends JpaRepository<TraductionEntry, Long> {

    List<TraductionEntry> findByLangue_CodeOrderByCleAsc(String langueCode);

    List<TraductionEntry> findByLangue_CodeAndScopeOrderByCleAsc(String langueCode, String scope);

    Optional<TraductionEntry> findByCleAndLangue_Code(String cle, String langueCode);

    List<TraductionEntry> findTop1ByLangue_CodeOrderByLastUpdateDesc(String langueCode);
}
