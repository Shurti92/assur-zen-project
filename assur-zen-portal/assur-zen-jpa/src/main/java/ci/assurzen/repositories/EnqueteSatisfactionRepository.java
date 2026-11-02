package ci.assurzen.repositories;

import ci.assurzen.entities.EnqueteSatisfaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnqueteSatisfactionRepository extends JpaRepository<EnqueteSatisfaction, Long> {

    Optional<EnqueteSatisfaction> findByCode(String code);

    List<EnqueteSatisfaction> findByInsuredId(Long insuredId);
}
