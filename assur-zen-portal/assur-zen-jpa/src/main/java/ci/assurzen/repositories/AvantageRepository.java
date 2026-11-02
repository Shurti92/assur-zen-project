package ci.assurzen.repositories;

import ci.assurzen.entities.Avantage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AvantageRepository extends JpaRepository<Avantage, Long> {

    Optional<Avantage> findByCode(String code);

    List<Avantage> findByPalierIdAndActiveTrue(Long palierId);
}
