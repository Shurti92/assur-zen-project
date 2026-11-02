package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.entities.FundOutflow;
import ci.assurzen.entities.FundOutflowType;
import ci.assurzen.entities.InstallmentStatus;
import ci.assurzen.entities.Loan;
import ci.assurzen.entities.LoanInstallment;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.RepaymentFrequency;
import ci.assurzen.repositories.LoanInstallmentRepository;
import ci.assurzen.repositories.LoanRepaymentRepository;
import ci.assurzen.repositories.LoanRepository;
import ci.assurzen.repositories.PartenaireRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Couvre la generation d'echeancier (repartition egale, reliquat d'arrondi absorbe par la derniere
 * echeance - §53) et le recalcul du solde (§20, jamais mis en cache). */
class LoanServiceTest {

    private record Fixture(
            LoanService service,
            LoanRepository loanRepository,
            LoanInstallmentRepository installmentRepository,
            LoanRepaymentRepository repaymentRepository,
            PartenaireRepository partenaireRepository,
            CodeGeneratorService codeGeneratorService
    ) {
    }

    private Fixture newFixture() {
        LoanRepository loanRepository = mock(LoanRepository.class);
        LoanInstallmentRepository installmentRepository = mock(LoanInstallmentRepository.class);
        LoanRepaymentRepository repaymentRepository = mock(LoanRepaymentRepository.class);
        PartenaireRepository partenaireRepository = mock(PartenaireRepository.class);
        CodeGeneratorService codeGeneratorService = mock(CodeGeneratorService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);
        LoanRepaymentService loanRepaymentService = mock(LoanRepaymentService.class);

        when(codeGeneratorService.generate(any())).thenReturn("PRET2026000001");
        AtomicLong idSeq = new AtomicLong(1);
        when(loanRepository.save(any())).thenAnswer(inv -> {
            Loan l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId(idSeq.getAndIncrement());
            }
            return l;
        });
        when(installmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanService service = new LoanService(loanRepository, installmentRepository, repaymentRepository,
                partenaireRepository, codeGeneratorService, functionalAuditRecorder, loanRepaymentService);

        return new Fixture(service, loanRepository, installmentRepository, repaymentRepository, partenaireRepository, codeGeneratorService);
    }

    @Test
    void createFromDisbursedOutflow_sansConditionsDePret_estRejeteAvec400() {
        Fixture fx = newFixture();
        FundOutflow outflow = FundOutflow.builder().id(1L).reference("DEC2026000001")
                .outflowType(FundOutflowType.builder().id(1L).code("LOAN").build())
                .amount(1000000L).currency("XOF")
                .beneficiaryType("PARTNER").beneficiaryId(33L)
                .build(); // aucune condition de pret renseignee (loanStartDate/etc null)

        org.springframework.web.server.ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> fx.service().createFromDisbursedOutflow(outflow));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createFromDisbursedOutflow_genereLEcheancierAvecReliquatSurLaDerniere() {
        Fixture fx = newFixture();
        Partenaire partner = Partenaire.builder().id(33L).nom("Test").prenoms("Partenaire").numeroWaveCommission("0700000000").build();
        when(fx.partenaireRepository().findById(33L)).thenReturn(Optional.of(partner));

        FundOutflow outflow = FundOutflow.builder().id(1L).reference("DEC2026000001")
                .outflowType(FundOutflowType.builder().id(1L).code("LOAN").build())
                .amount(1000000L).currency("XOF")
                .beneficiaryType("PARTNER").beneficiaryId(33L)
                .loanInterestRate(BigDecimal.ZERO)
                .loanStartDate(LocalDate.of(2026, 9, 1))
                .loanExpectedEndDate(LocalDate.of(2027, 2, 1))
                .loanRepaymentFrequency(RepaymentFrequency.MONTHLY)
                .loanNumberOfInstallments(3)
                .build();

        fx.service().createFromDisbursedOutflow(outflow);

        ArgumentCaptor<LoanInstallment> captor = ArgumentCaptor.forClass(LoanInstallment.class);
        verify(fx.installmentRepository(), times(3)).save(captor.capture());
        List<LoanInstallment> installments = captor.getAllValues();

        // 1000000 / 3 = 333333 par echeance, reliquat 1 absorbe par la 3e (333333+333333+333334=1000000)
        assertEquals(333333L, installments.get(0).getTotalDue());
        assertEquals(333333L, installments.get(1).getTotalDue());
        assertEquals(333334L, installments.get(2).getTotalDue());
        long total = installments.stream().mapToLong(LoanInstallment::getTotalDue).sum();
        assertEquals(1000000L, total);
        assertEquals(InstallmentStatus.PENDING, installments.get(0).getStatus());
    }

    @Test
    void balance_recalculeToujoursParRequete() {
        Fixture fx = newFixture();
        Loan loan = Loan.builder().id(1L).principalAmount(1000000L).totalAmount(1000000L).build();
        when(fx.loanRepository().findById(1L)).thenReturn(Optional.of(loan));
        when(fx.repaymentRepository().sumAmountByLoanId(1L)).thenReturn(400000L);

        var balance = fx.service().balance(1L);

        assertEquals(600000L, balance.remainingBalance());
        assertEquals(400000L, balance.totalRepaid());
    }
}
