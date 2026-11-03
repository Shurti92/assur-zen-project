package ci.assurzen.repositories;

import ci.assurzen.entities.SupportRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {

    List<SupportRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SupportRequest> findByIdAndUserId(Long id, Long userId);

    List<SupportRequest> findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(Long userId, Instant since);

    List<SupportRequest> findTop200ByOrderByCreatedAtDesc();

    List<SupportRequest> findTop200ByStatutOrderByCreatedAtDesc(String statut);
}
