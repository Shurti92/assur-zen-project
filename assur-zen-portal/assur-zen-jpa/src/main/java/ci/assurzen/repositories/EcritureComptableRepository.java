package ci.assurzen.repositories;

import ci.assurzen.entities.EcritureComptable;
import ci.assurzen.entities.TypeOperationLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EcritureComptableRepository extends JpaRepository<EcritureComptable, Long> {

    List<EcritureComptable> findByContractIdOrderByDateEcritureAsc(Long contractId);

    Optional<EcritureComptable> findFirstByContractIdAndTypeOperationAndContrepasseeFalseOrderByDateEcritureDesc(
            Long contractId, TypeOperationLedger typeOperation);
}
