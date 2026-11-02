package ci.assurzen.repositories;

import ci.assurzen.entities.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByToken(String token);

    /** Verrou pessimiste sur le refresh token presente (2026-10-04) : deux renouvellements simultanes du
     * meme jeton ne peuvent plus le consommer chacun de leur cote. Requete NATIVE (pas @Lock + JPQL) :
     * meme bug de dialecte MariaDB que CommissionPartialPaymentRequestRepository.findByIdForUpdate. */
    @Query(value = "select * from t_assur_zen_user_session where token = :token for update", nativeQuery = true)
    Optional<UserSession> findByTokenForUpdate(@Param("token") String token);

    /** La session (sessionId) a-t-elle encore un jeton actif ? Faux apres deconnexion ou changement de mot de passe. */
    boolean existsBySessionIdAndRevokedFalse(String sessionId);

    List<UserSession> findAllByUserId(Long userId);

    List<UserSession> findAllByUserIdAndRevokedFalse(Long userId);

    long deleteByRevokedTrue();

    long countByRevokedFalse();
}
