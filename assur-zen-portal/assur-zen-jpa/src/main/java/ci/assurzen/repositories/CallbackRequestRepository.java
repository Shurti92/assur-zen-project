package ci.assurzen.repositories;

import ci.assurzen.entities.CallbackRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CallbackRequestRepository extends JpaRepository<CallbackRequest, Long> {

    List<CallbackRequest> findByPhoneAndCreatedAtAfterOrderByCreatedAtAsc(String phone, Instant since);

    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant since);

    Optional<CallbackRequest> findFirstByPhoneAndStatutOrderByCreatedAtDesc(String phone, String statut);

    java.util.List<CallbackRequest> findTop200ByOrderByCreatedAtDesc();

    java.util.List<CallbackRequest> findTop200ByStatutOrderByCreatedAtDesc(String statut);
}
