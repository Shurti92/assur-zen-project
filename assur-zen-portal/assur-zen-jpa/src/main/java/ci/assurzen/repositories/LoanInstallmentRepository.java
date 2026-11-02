package ci.assurzen.repositories;

import ci.assurzen.entities.InstallmentStatus;
import ci.assurzen.entities.LoanInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, Long> {

    List<LoanInstallment> findByLoanIdOrderByInstallmentNumberAsc(Long loanId);

    /** Echeances impayees (PENDING/PARTIALLY_PAID) les plus anciennes en premier - base de
     * l'allocation FIFO d'un remboursement (§19). */
    List<LoanInstallment> findByLoanIdAndStatusInOrderByDueDateAsc(Long loanId, List<InstallmentStatus> statuses);

    List<LoanInstallment> findByStatusInAndDueDateBefore(List<InstallmentStatus> statuses, LocalDate before);

    /** Rappels de pret (§ rappels d'echeance) : echeances impayees dont la date d'echeance tombe
     * exactement sur l'une des dates fournies. */
    List<LoanInstallment> findByStatusInAndDueDateIn(List<InstallmentStatus> statuses, List<LocalDate> dueDates);

    List<LoanInstallment> findByStatusInAndDueDate(List<InstallmentStatus> statuses, LocalDate dueDate);
}
