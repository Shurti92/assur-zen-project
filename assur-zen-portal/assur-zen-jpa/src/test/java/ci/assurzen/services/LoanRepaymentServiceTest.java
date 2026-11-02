package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.LoanRepaymentResponse;
import ci.assurzen.entities.DisbursementMethod;
import ci.assurzen.entities.FundOutflow;
import ci.assurzen.entities.FundOutflowStatus;
import ci.assurzen.entities.InstallmentStatus;
import ci.assurzen.entities.Loan;
import ci.assurzen.entities.LoanInstallment;
import ci.assurzen.entities.LoanRepayment;
import ci.assurzen.repositories.FundOutflowRepository;
import ci.assurzen.repositories.LoanInstallmentRepository;
import ci.assurzen.repositories.LoanRepaymentAllocationRepository;
import ci.assurzen.repositories.LoanRepaymentRepository;
import ci.assurzen.repositories.LoanRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Couvre l'allocation FIFO d'un remboursement sur les echeances impayees (§19), le refus d'un
 * remboursement superieur au solde restant (Regle 6), et la cloture automatique quand le solde
 * devient nul (§22/§68). */
class LoanRepaymentServiceTest {

    private record Fixture(
            LoanRepaymentService service,
            LoanRepository loanRepository,
            LoanInstallmentRepository installmentRepository,
            LoanRepaymentRepository repaymentRepository,
            LoanRepaymentAllocationRepository allocationRepository,
            FundOutflowRepository fundOutflowRepository,
            AuthService authService
    ) {
    }

    private Fixture newFixture() {
        LoanRepository loanRepository = mock(LoanRepository.class);
        LoanInstallmentRepository installmentRepository = mock(LoanInstallmentRepository.class);
        LoanRepaymentRepository repaymentRepository = mock(LoanRepaymentRepository.class);
        LoanRepaymentAllocationRepository allocationRepository = mock(LoanRepaymentAllocationRepository.class);
        FundOutflowRepository fundOutflowRepository = mock(FundOutflowRepository.class);
        CodeGeneratorService codeGeneratorService = mock(CodeGeneratorService.class);
        AuthService authService = mock(AuthService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        when(codeGeneratorService.generate(any())).thenReturn("RMB2026000001");
        when(repaymentRepository.lockTousPourPret(any())).thenReturn(List.of());
        AtomicLong repaymentIdSeq = new AtomicLong(1);
        when(repaymentRepository.save(any())).thenAnswer(inv -> {
            LoanRepayment r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(repaymentIdSeq.getAndIncrement());
            }
            return r;
        });
        when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(allocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(allocationRepository.sumAllocatedByInstallmentId(anyLong())).thenReturn(0L);
        when(fundOutflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanRepaymentService service = new LoanRepaymentService(loanRepository, installmentRepository, repaymentRepository,
                allocationRepository, fundOutflowRepository, codeGeneratorService, authService, functionalAuditRecorder);

        return new Fixture(service, loanRepository, installmentRepository, repaymentRepository, allocationRepository,
                fundOutflowRepository, authService);
    }

    private Loan loan(long id, long totalAmount) {
        FundOutflow outflow = FundOutflow.builder().id(1L).reference("DEC2026000001").status(FundOutflowStatus.DISBURSED).build();
        return Loan.builder().id(id).loanReference("PRET2026000001").currency("XOF").totalAmount(totalAmount)
                .principalAmount(totalAmount).fundOutflow(outflow).build();
    }

    private LoanInstallment installment(long id, Loan loan, int number, long totalDue, InstallmentStatus status) {
        return LoanInstallment.builder().id(id).loan(loan).installmentNumber(number).dueDate(LocalDate.now())
                .totalDue(totalDue).principalAmount(totalDue).interestAmount(0L).status(status).build();
    }

    @Test
    void registerRepayment_montantSuperieurAuSolde_estRejeteAvec400_Regle6() {
        Fixture fx = newFixture();
        Loan loan = loan(1L, 1000000L);
        when(fx.loanRepository().findById(1L)).thenReturn(Optional.of(loan));
        when(fx.repaymentRepository().sumAmountByLoanId(1L)).thenReturn(0L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().registerRepayment(1L, 1500000L, LocalDate.now(), DisbursementMethod.BANK_TRANSFER, null, null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void registerRepayment_pretDejaSolde_estRejeteAvec409_Regle8() {
        Fixture fx = newFixture();
        Loan loan = loan(1L, 1000000L);
        when(fx.loanRepository().findById(1L)).thenReturn(Optional.of(loan));
        when(fx.repaymentRepository().sumAmountByLoanId(1L)).thenReturn(1000000L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().registerRepayment(1L, 100000L, LocalDate.now(), DisbursementMethod.BANK_TRANSFER, null, null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void registerRepayment_couvrePlusieursEcheances_allocationFifo() {
        Fixture fx = newFixture();
        Loan loan = loan(1L, 900000L);
        when(fx.loanRepository().findById(1L)).thenReturn(Optional.of(loan));
        when(fx.repaymentRepository().sumAmountByLoanId(1L)).thenReturn(0L);

        LoanInstallment i1 = installment(1L, loan, 1, 300000L, InstallmentStatus.PENDING);
        LoanInstallment i2 = installment(2L, loan, 2, 300000L, InstallmentStatus.PENDING);
        LoanInstallment i3 = installment(3L, loan, 3, 300000L, InstallmentStatus.PENDING);
        when(fx.installmentRepository().findByLoanIdAndStatusInOrderByDueDateAsc(any(), any()))
                .thenReturn(List.of(i1, i2, i3));

        // Rembourse 450000 : doit soldes i1 (300000) et couvrir partiellement i2 (150000/300000)
        LoanRepaymentResponse response = fx.service().registerRepayment(1L, 450000L, LocalDate.now(), DisbursementMethod.WAVE, null, null);

        assertEquals(2, response.allocations().size());
        assertEquals(300000L, response.allocations().get(0).allocatedAmount());
        assertEquals(150000L, response.allocations().get(1).allocatedAmount());
        assertEquals(InstallmentStatus.PAID, i1.getStatus());
        assertEquals(InstallmentStatus.PARTIALLY_PAID, i2.getStatus());
        assertEquals(InstallmentStatus.PENDING, i3.getStatus());
    }

    @Test
    void registerRepayment_soldeIntegral_clotureAutomatiquementLaSortieDeFonds() {
        Fixture fx = newFixture();
        Loan loan = loan(1L, 500000L);
        when(fx.loanRepository().findById(1L)).thenReturn(Optional.of(loan));
        when(fx.repaymentRepository().sumAmountByLoanId(1L)).thenReturn(0L);
        LoanInstallment i1 = installment(1L, loan, 1, 500000L, InstallmentStatus.PENDING);
        when(fx.installmentRepository().findByLoanIdAndStatusInOrderByDueDateAsc(any(), any())).thenReturn(List.of(i1));

        fx.service().registerRepayment(1L, 500000L, LocalDate.now(), DisbursementMethod.CASH, null, null);

        assertEquals(FundOutflowStatus.CLOSED, loan.getFundOutflow().getStatus());
    }

    @Test
    void registerRepayment_remboursementPartiel_passeEnPartiallyRepaid() {
        Fixture fx = newFixture();
        Loan loan = loan(1L, 500000L);
        when(fx.loanRepository().findById(1L)).thenReturn(Optional.of(loan));
        when(fx.repaymentRepository().sumAmountByLoanId(1L)).thenReturn(0L);
        LoanInstallment i1 = installment(1L, loan, 1, 500000L, InstallmentStatus.PENDING);
        when(fx.installmentRepository().findByLoanIdAndStatusInOrderByDueDateAsc(any(), any())).thenReturn(List.of(i1));

        fx.service().registerRepayment(1L, 200000L, LocalDate.now(), DisbursementMethod.CASH, null, null);

        assertEquals(FundOutflowStatus.PARTIALLY_REPAID, loan.getFundOutflow().getStatus());
    }
}
