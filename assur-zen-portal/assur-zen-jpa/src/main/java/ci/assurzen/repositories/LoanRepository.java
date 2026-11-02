package ci.assurzen.repositories;

import ci.assurzen.entities.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findByFundOutflowId(Long fundOutflowId);

    long countByPartnerId(Long partnerId);
}
