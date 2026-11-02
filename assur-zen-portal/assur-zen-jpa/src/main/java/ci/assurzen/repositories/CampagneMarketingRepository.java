package ci.assurzen.repositories;

import ci.assurzen.entities.CampagneMarketing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampagneMarketingRepository extends JpaRepository<CampagneMarketing, Long> {

    Optional<CampagneMarketing> findByCode(String code);
}
