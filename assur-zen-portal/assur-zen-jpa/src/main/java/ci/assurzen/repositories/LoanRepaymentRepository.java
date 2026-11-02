package ci.assurzen.repositories;

import ci.assurzen.entities.LoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {

    List<LoanRepayment> findByLoanIdOrderByCreatedAtDesc(Long loanId);

    /** SUM(amount) recalcule a chaque appel - jamais de solde mis en cache/incremente en memoire
     * (meme discipline que les compteurs Flotte/Prime Objectif). Renvoie 0 (pas null) via COALESCE. */
    @Query("select coalesce(sum(r.amount), 0) from LoanRepayment r where r.loan.id = :loanId")
    long sumAmountByLoanId(@Param("loanId") Long loanId);

    /** Verrou de plage sur tous les remboursements de ce pret - meme patron et meme raison que
     * CommissionPartialPaymentRequestRepository.lockToutesPourPartenaire : protege le calcul du
     * solde net contre un INSERT concurrent (anti-phantom-read sous REPEATABLE READ), pas seulement
     * les UPDATE. Requete NATIVE pour eviter le "for update of <alias>" invalide sur MariaDB. */
    @Query(value = "select * from t_assur_zen_loan_repayments where loan_id = :loanId for update", nativeQuery = true)
    List<LoanRepayment> lockTousPourPret(@Param("loanId") Long loanId);
}
