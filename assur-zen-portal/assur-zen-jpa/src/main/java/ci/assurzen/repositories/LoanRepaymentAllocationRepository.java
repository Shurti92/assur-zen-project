package ci.assurzen.repositories;

import ci.assurzen.entities.LoanRepaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanRepaymentAllocationRepository extends JpaRepository<LoanRepaymentAllocation, Long> {

    @Query("select coalesce(sum(a.allocatedAmount), 0) from LoanRepaymentAllocation a where a.installment.id = :installmentId")
    long sumAllocatedByInstallmentId(@Param("installmentId") Long installmentId);
}
