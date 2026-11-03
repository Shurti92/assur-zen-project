package ci.assurzen.repositories;

import ci.assurzen.entities.ContractCustomerRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContractCustomerRequestRepository extends JpaRepository<ContractCustomerRequest, Long> {

    Optional<ContractCustomerRequest> findFirstByContractIdAndTypeAndStatutOrderByCreatedAtDesc(Long contractId, String type, String statut);

    java.util.List<ContractCustomerRequest> findTop200ByOrderByCreatedAtDesc();

    java.util.List<ContractCustomerRequest> findTop200ByStatutOrderByCreatedAtDesc(String statut);
}
