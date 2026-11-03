package ci.assurzen.repositories;

import ci.assurzen.entities.MobilePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MobilePaymentRepository extends JpaRepository<MobilePayment, Long> {

    Optional<MobilePayment> findByReferenceAndUserId(String reference, Long userId);

    Optional<MobilePayment> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    boolean existsByPayableReferenceAndStatus(String payableReference, String status);

    Optional<MobilePayment> findFirstByPayableReferenceAndStatusAndExpiresAtAfter(String payableReference, String status, Instant now);

    List<MobilePayment> findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(Long userId, Instant since);
}
