package ci.assurzen.repositories;

import ci.assurzen.entities.FundOutflowType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FundOutflowTypeRepository extends JpaRepository<FundOutflowType, Long> {

    Optional<FundOutflowType> findByCodeIgnoreCase(String code);

    List<FundOutflowType> findByActifTrueOrderByOrdreAsc();
}
