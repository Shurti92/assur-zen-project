package ci.assurzen.repositories;

import ci.assurzen.entities.LienSuiviVignette;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LienSuiviVignetteRepository extends JpaRepository<LienSuiviVignette, Long> {

    Optional<LienSuiviVignette> findByTokenHash(String tokenHash);

    Optional<LienSuiviVignette> findByDemandeVignetteMotoIdAndActifTrue(Long demandeVignetteMotoId);
}
