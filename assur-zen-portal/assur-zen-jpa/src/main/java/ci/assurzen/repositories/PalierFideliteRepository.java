package ci.assurzen.repositories;

import ci.assurzen.entities.PalierFidelite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PalierFideliteRepository extends JpaRepository<PalierFidelite, Long> {

    Optional<PalierFidelite> findByCode(String code);

    List<PalierFidelite> findByActiveTrueOrderBySeuilPointsAsc();

    Optional<PalierFidelite> findFirstByActiveTrueAndSeuilPointsLessThanEqualOrderBySeuilPointsDesc(Long pointsCumules);
}
