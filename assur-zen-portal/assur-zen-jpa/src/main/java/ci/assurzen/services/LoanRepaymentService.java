package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CreateLoanRepaymentRequest;
import ci.assurzen.dto.LoanRepaymentAllocationResponse;
import ci.assurzen.dto.LoanRepaymentResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.DisbursementMethod;
import ci.assurzen.entities.FundOutflow;
import ci.assurzen.entities.FundOutflowStatus;
import ci.assurzen.entities.InstallmentStatus;
import ci.assurzen.entities.Loan;
import ci.assurzen.entities.LoanInstallment;
import ci.assurzen.entities.LoanRepayment;
import ci.assurzen.entities.LoanRepaymentAllocation;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.FundOutflowRepository;
import ci.assurzen.repositories.LoanInstallmentRepository;
import ci.assurzen.repositories.LoanRepaymentAllocationRepository;
import ci.assurzen.repositories.LoanRepaymentRepository;
import ci.assurzen.repositories.LoanRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Enregistrement des remboursements (§19-20). Allocation FIFO sur les echeances impayees les plus
 * anciennes - un remboursement peut couvrir une seule echeance, plusieurs, ou en couvrir une
 * partiellement (§19). Verrou pessimiste sur toutes les lignes de remboursement du pret en toute
 * premiere instruction (meme patron que CommissionPartialPaymentService, cf. LoanRepaymentRepository).
 */
@Service
@RequiredArgsConstructor
public class LoanRepaymentService {

    private static final List<InstallmentStatus> OPEN_STATUSES = List.of(InstallmentStatus.PENDING, InstallmentStatus.PARTIALLY_PAID);

    private final LoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final LoanRepaymentAllocationRepository allocationRepository;
    private final FundOutflowRepository fundOutflowRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    @Transactional
    public LoanRepaymentResponse registerRepayment(Long loanId, CreateLoanRepaymentRequest request) {
        return registerRepayment(loanId, request.amount(), request.paymentDate(), request.paymentMethod(),
                request.paymentReference(), request.comment());
    }

    @Transactional
    public LoanRepaymentResponse registerRepayment(Long loanId, Long amount, LocalDate paymentDate,
                                                     DisbursementMethod paymentMethod, String paymentReference, String comment) {
        // Verrou en TOUTE PREMIERE instruction, avant meme resolveLoan() - meme raison documentee
        // dans CommissionPartialPaymentService : sous REPEATABLE READ, la vue d'une transaction se
        // fige des sa premiere lecture non verrouillee, avant meme d'atteindre ce verrou si un autre
        // SELECT le precede.
        repaymentRepository.lockTousPourPret(loanId);

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pret introuvable: " + loanId));

        if (amount == null || amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le montant du remboursement doit etre positif");
        }
        long dejaRembourse = repaymentRepository.sumAmountByLoanId(loanId);
        long soldeRestant = loan.getTotalAmount() - dejaRembourse;
        if (soldeRestant <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce pret est deja solde (Regle 8)");
        }
        if (amount > soldeRestant) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le montant depasse le solde restant (" + soldeRestant + " " + loan.getCurrency() + ", Regle 6)");
        }

        String username = authService.currentPrincipal().username();
        LoanRepayment repayment = LoanRepayment.builder()
                .loan(loan)
                .reference(codeGeneratorService.generate(CodePrefix.LOAN_REPAYMENT))
                .amount(amount)
                .currency(loan.getCurrency())
                .paymentMethod(paymentMethod)
                .paymentReference(paymentReference)
                .paymentDate(paymentDate)
                .receivedBy(username)
                .comment(comment)
                .build();
        repayment = repaymentRepository.save(repayment);

        List<LoanRepaymentAllocationResponse> allocations = allocate(loan, repayment, amount);

        long nouveauTotalRembourse = dejaRembourse + amount;
        updateFundOutflowAfterRepayment(loan, nouveauTotalRembourse, username);

        functionalAuditRecorder.recordAction("SORTIES_FONDS", "Loan", loan.getLoanReference(), ActionAudit.REPAY,
                "Remboursement de " + amount + " " + loan.getCurrency() + " enregistre");
        if (nouveauTotalRembourse >= loan.getTotalAmount()) {
            functionalAuditRecorder.recordAction("SORTIES_FONDS", "FundOutflow", loan.getFundOutflow().getReference(),
                    ActionAudit.CLOSE, "Pret solde (REPAID) - cloture automatique");
        }

        return new LoanRepaymentResponse(repayment.getId(), repayment.getReference(), repayment.getAmount(),
                repayment.getCurrency(), repayment.getPaymentMethod(), repayment.getPaymentReference(),
                repayment.getPaymentDate(), repayment.getReceivedBy(), repayment.getComment(), allocations, repayment.getCreatedAt());
    }

    private List<LoanRepaymentAllocationResponse> allocate(Loan loan, LoanRepayment repayment, long amount) {
        List<LoanInstallmentResponseHolder> allocations = new ArrayList<>();
        long remaining = amount;

        List<LoanInstallment> openInstallments = installmentRepository.findByLoanIdAndStatusInOrderByDueDateAsc(loan.getId(), OPEN_STATUSES);
        for (LoanInstallment installment : openInstallments) {
            if (remaining <= 0) {
                break;
            }
            long alreadyAllocated = allocationRepository.sumAllocatedByInstallmentId(installment.getId());
            long installmentRemaining = installment.getTotalDue() - alreadyAllocated;
            if (installmentRemaining <= 0) {
                continue;
            }
            long toAllocate = Math.min(remaining, installmentRemaining);

            allocationRepository.save(LoanRepaymentAllocation.builder()
                    .repayment(repayment)
                    .installment(installment)
                    .allocatedAmount(toAllocate)
                    .build());

            long newAllocated = alreadyAllocated + toAllocate;
            if (newAllocated >= installment.getTotalDue()) {
                installment.setStatus(InstallmentStatus.PAID);
                installment.setPaidAt(Instant.now());
            } else {
                installment.setStatus(InstallmentStatus.PARTIALLY_PAID);
            }
            installmentRepository.save(installment);

            allocations.add(new LoanInstallmentResponseHolder(installment.getId(), installment.getInstallmentNumber(), toAllocate));
            remaining -= toAllocate;
        }

        return allocations.stream()
                .map(h -> new LoanRepaymentAllocationResponse(h.installmentId(), h.installmentNumber(), h.amount()))
                .toList();
    }

    private record LoanInstallmentResponseHolder(Long installmentId, Integer installmentNumber, Long amount) {
    }

    /** Solde nul -> REPAID puis CLOTURE automatique (LOAN=CLOSED / FUND_OUTFLOW=CLOSED, §22/§68) -
     * sans passer par FundOutflowService.close() (garde reservee aux appels utilisateur explicites) :
     * cette transition est declenchee par le systeme, pas par un clic admin. */
    private void updateFundOutflowAfterRepayment(Loan loan, long totalRembourse, String username) {
        FundOutflow outflow = loan.getFundOutflow();
        boolean solde = totalRembourse >= loan.getTotalAmount();
        outflow.setStatus(solde ? FundOutflowStatus.CLOSED : FundOutflowStatus.PARTIALLY_REPAID);
        if (solde) {
            outflow.setClosedAt(Instant.now());
            outflow.setClosedBy(username);
        }
        outflow.setUpdatedBy(username);
        fundOutflowRepository.save(outflow);
    }

    List<LoanRepaymentResponse> listForLoan(Long loanId) {
        return repaymentRepository.findByLoanIdOrderByCreatedAtDesc(loanId).stream()
                .map(r -> new LoanRepaymentResponse(r.getId(), r.getReference(), r.getAmount(), r.getCurrency(),
                        r.getPaymentMethod(), r.getPaymentReference(), r.getPaymentDate(), r.getReceivedBy(), r.getComment(),
                        List.of(), r.getCreatedAt()))
                .toList();
    }

    long paidAmountForInstallment(Long installmentId) {
        return allocationRepository.sumAllocatedByInstallmentId(installmentId);
    }
}
