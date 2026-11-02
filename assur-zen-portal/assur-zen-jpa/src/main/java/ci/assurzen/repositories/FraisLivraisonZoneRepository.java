package ci.assurzen.repositories;

import ci.assurzen.entities.FraisLivraisonZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FraisLivraisonZoneRepository extends JpaRepository<FraisLivraisonZone, Long> {

    List<FraisLivraisonZone> findAllByOrderByCreatedAtDesc();

    Optional<FraisLivraisonZone> findByCodeAndActifTrue(String code);

    boolean existsByCode(String code);
}
