package ci.assurzen.repositories;

import ci.assurzen.entities.CommissionPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommissionPaymentRepository extends JpaRepository<CommissionPayment, Long> {

    Page<CommissionPayment> findByPartenaireId(Long partenaireId, Pageable pageable);

    boolean existsByContractId(Long contractId);

    /** Releves mensuels des commissions versees (2026-10-03). */
    java.util.List<CommissionPayment> findByPartenaireIdOrderByPaidAtAsc(Long partenaireId);

    /** Idempotence de l'enregistrement d'un virement SycaPay confirme (2026-09-23). */
    boolean existsBySycapayTransactionId(Long sycapayTransactionId);
}
