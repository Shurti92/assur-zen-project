package ci.assurzen.repositories;

import ci.assurzen.entities.FundOutflowDisbursement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FundOutflowDisbursementRepository extends JpaRepository<FundOutflowDisbursement, Long> {

    Optional<FundOutflowDisbursement> findFirstByFundOutflowIdOrderByCreatedAtDesc(Long fundOutflowId);

    /** Idempotence par cle fournie par le client - patron identique a
     * CommissionPartialPaymentRequestRepository.findByPartenaireIdAndIdempotencyKey. */
    Optional<FundOutflowDisbursement> findByFundOutflowIdAndIdempotencyKey(Long fundOutflowId, String idempotencyKey);

    /** Verrou de plage sur toutes les lignes de decaissement de cette sortie - protege aussi contre
     * un INSERT concurrent (anti-phantom-read), meme cible de verrou que
     * CommissionPartialPaymentRequestRepository.lockToutesPourPartenaire et pour la meme raison :
     * un verrou sur t_assur_zen_fund_outflows seul ne serialise pas les lectures/insertions dans
     * CETTE table. */
    @Query(value = "select * from t_assur_zen_fund_outflow_disbursements where fund_outflow_id = :fundOutflowId for update", nativeQuery = true)
    List<FundOutflowDisbursement> lockToutesPourSortie(@Param("fundOutflowId") Long fundOutflowId);
}
