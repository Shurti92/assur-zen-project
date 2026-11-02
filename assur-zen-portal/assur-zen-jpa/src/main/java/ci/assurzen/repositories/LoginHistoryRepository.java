package ci.assurzen.repositories;

import ci.assurzen.entities.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    long countByPartenaireIdAndCreatedAtGreaterThanEqual(Long partenaireId, Instant since);

    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, Instant since);

    long countByCreatedAtGreaterThanEqual(Instant since);

    // ---- Connexions REUSSIES seulement (echec IS NULL) : statistiques admin, depuis 2026-10-03 que les
    // echecs sont aussi historises ----

    long countByPartenaireIdAndCreatedAtGreaterThanEqualAndEchecIsNull(Long partenaireId, Instant since);

    long countByUserIdAndCreatedAtGreaterThanEqualAndEchecIsNull(Long userId, Instant since);

    long countByCreatedAtGreaterThanEqualAndEchecIsNull(Instant since);

    /** Historique self-service de l'utilisateur connecte, reussites et echecs. */
    org.springframework.data.domain.Page<LoginHistory> findByUserIdOrderByCreatedAtDesc(Long userId,
                                                                                      org.springframework.data.domain.Pageable pageable);

    java.util.Optional<LoginHistory> findByIdAndUserId(Long id, Long userId);

    /** Dernieres connexions reussies : detection d'un appareil inconnu (2026-10-04). */
    java.util.List<LoginHistory> findTop100ByUserIdAndEchecIsNullOrderByCreatedAtDesc(Long userId);
}
