package ci.assurzen.repositories;

import ci.assurzen.entities.AccountDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, Long> {

    Optional<AccountDeletionRequest> findFirstByUserIdAndStatutOrderByRequestedAtDesc(Long userId, String statut);
}
