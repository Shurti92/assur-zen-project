package ci.assurzen.repositories;

import ci.assurzen.entities.CompteFideliteAvantage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompteFideliteAvantageRepository extends JpaRepository<CompteFideliteAvantage, Long> {

    List<CompteFideliteAvantage> findByCompteFideliteIdOrderByDateAttributionDesc(Long compteFideliteId);

    List<CompteFideliteAvantage> findByCompteFideliteIdAndActifTrue(Long compteFideliteId);

    Optional<CompteFideliteAvantage> findByCompteFideliteIdAndAvantageIdAndActifTrue(Long compteFideliteId, Long avantageId);
}
