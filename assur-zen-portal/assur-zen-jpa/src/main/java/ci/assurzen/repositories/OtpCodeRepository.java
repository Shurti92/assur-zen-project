package ci.assurzen.repositories;

import ci.assurzen.entities.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    List<OtpCode> findAllByPhoneAndConsumedFalse(String phone);

    Optional<OtpCode> findTopByPhoneAndConsumedFalseOrderByCreatedAtDesc(String phone);

    Optional<OtpCode> findTopByPhoneOrderByCreatedAtDesc(String phone);

    /** Codes demandes pour ce numero depuis l'instant donne (quota horaire mobile, 2026-11-02). */
    List<OtpCode> findByPhoneAndMobileRequestTrueAndCreatedAtAfterOrderByCreatedAtAsc(String phone, java.time.Instant since);
}
