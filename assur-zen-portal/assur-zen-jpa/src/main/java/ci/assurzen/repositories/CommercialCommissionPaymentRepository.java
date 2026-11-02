package ci.assurzen.repositories;

import ci.assurzen.entities.CommercialCommissionPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommercialCommissionPaymentRepository extends JpaRepository<CommercialCommissionPayment, Long> {

    Page<CommercialCommissionPayment> findByCommercialId(Long commercialId, Pageable pageable);

    boolean existsByContractId(Long contractId);

    /** Idempotence de l'enregistrement d'un virement SycaPay confirme par reconciliation (2026-09-23). */
    boolean existsBySycapayTransactionId(Long sycapayTransactionId);
}
