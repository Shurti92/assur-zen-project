package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.EarlyRepaymentRequest;
import ci.assurzen.dto.LoanBalanceResponse;
import ci.assurzen.dto.LoanInstallmentResponse;
import ci.assurzen.dto.LoanRepaymentResponse;
import ci.assurzen.dto.LoanResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.FundOutflow;
import ci.assurzen.entities.InstallmentStatus;
import ci.assurzen.entities.Loan;
import ci.assurzen.entities.LoanInstallment;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.LoanInstallmentRepository;
import ci.assurzen.repositories.LoanRepaymentRepository;
import ci.assurzen.repositories.LoanRepository;
import ci.assurzen.repositories.PartenaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Moteur Pret (§16-22), extension specialisee d'un FundOutflow de type LOAN. Le solde n'est JAMAIS
 * mis en cache : toujours recalcule par requete SUM sur les remboursements (meme discipline que les
 * compteurs Flotte/Prime Objectif - §20 l'exige explicitement : "ne jamais faire confiance
 * uniquement a une valeur calculee cote frontend").
 */
@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final PartenaireRepository partenaireRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final FunctionalAuditRecorder functionalAuditRecorder;
    private final LoanRepaymentService loanRepaymentService;

    /** Appele par FundOutflowService.disburse() juste apres le passage a DISBURSED (§68). Genere
     * l'echeancier avec repartition egale entre echeances - le reliquat d'arrondi est absorbe par la
     * derniere echeance (§53, jamais de perte/gain silencieux sur du FCFA entier). */
    @Transactional
    public Loan createFromDisbursedOutflow(FundOutflow outflow) {
        // Les conditions du pret sont optionnelles a la creation de la sortie de fonds (peuvent etre
        // completees plus tard via PUT tant qu'elle reste DRAFT) mais indispensables ici : impossible
        // de generer un echeancier sans elles.
        if (outflow.getLoanStartDate() == null || outflow.getLoanExpectedEndDate() == null
                || outflow.getLoanRepaymentFrequency() == null || outflow.getLoanNumberOfInstallments() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Les conditions du pret (loanStartDate, loanExpectedEndDate, loanRepaymentFrequency, loanNumberOfInstallments) doivent etre renseignees avant le decaissement - completez-les via PUT /fund-outflows/{id}");
        }
        Partenaire partner = partenaireRepository.findById(outflow.getBeneficiaryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + outflow.getBeneficiaryId()));

        long principal = outflow.getAmount();
        long interest = outflow.getLoanInterestRate() != null && outflow.getLoanInterestRate().signum() > 0
                ? Math.round(principal * outflow.getLoanInterestRate().doubleValue() / 100.0)
                : 0L;
        long total = principal + interest;

        Loan loan = Loan.builder()
                .fundOutflow(outflow)
                .loanReference(codeGeneratorService.generate(CodePrefix.LOAN))
                .partner(partner)
                .partnerPaymentDestinationSnapshot(partner.getNumeroWaveCommission())
                .principalAmount(principal)
                .interestRate(outflow.getLoanInterestRate() != null ? outflow.getLoanInterestRate() : java.math.BigDecimal.ZERO)
                .totalAmount(total)
                .currency(outflow.getCurrency())
                .startDate(outflow.getLoanStartDate())
                .expectedEndDate(outflow.getLoanExpectedEndDate())
                .repaymentFrequency(outflow.getLoanRepaymentFrequency())
                .numberOfInstallments(outflow.getLoanNumberOfInstallments())
                .build();
        loan = loanRepository.save(loan);

        generateInstallments(loan, principal, interest, total);

        functionalAuditRecorder.recordAction("SORTIES_FONDS", "Loan", loan.getLoanReference(), ActionAudit.CREATE,
                "Pret cree suite au decaissement de " + outflow.getReference());
        return loan;
    }

    private void generateInstallments(Loan loan, long principal, long interest, long total) {
        int n = loan.getNumberOfInstallments();
        long principalPerInstallment = principal / n;
        long interestPerInstallment = interest / n;
        long totalPerInstallment = total / n;

        for (int i = 1; i <= n; i++) {
            boolean isLast = i == n;
            LocalDate dueDate = computeDueDate(loan, i);
            LoanInstallment installment = LoanInstallment.builder()
                    .loan(loan)
                    .installmentNumber(i)
                    .dueDate(dueDate)
                    .principalAmount(isLast ? principal - principalPerInstallment * (n - 1) : principalPerInstallment)
                    .interestAmount(isLast ? interest - interestPerInstallment * (n - 1) : interestPerInstallment)
                    .totalDue(isLast ? total - totalPerInstallment * (n - 1) : totalPerInstallment)
                    .status(InstallmentStatus.PENDING)
                    .build();
            installmentRepository.save(installment);
        }
    }

    private LocalDate computeDueDate(Loan loan, int installmentNumber) {
        LocalDate start = loan.getStartDate();
        return switch (loan.getRepaymentFrequency()) {
            case WEEKLY -> start.plusWeeks(installmentNumber);
            case BIWEEKLY -> start.plusWeeks(2L * installmentNumber);
            case MONTHLY -> start.plusMonths(installmentNumber);
            case QUARTERLY -> start.plusMonths(3L * installmentNumber);
            case CUSTOM -> start.plusMonths(installmentNumber);
        };
    }

    public LoanResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public PageResponse<LoanResponse> search(Long partnerId, String reference, LocalDate dateFrom, LocalDate dateTo,
                                              Long amountMin, Long amountMax, Boolean overdue, Integer page, Integer size) {
        String normalizedReference = StringUtils.hasText(reference) ? reference.trim().toUpperCase(Locale.ROOT) : null;

        List<LoanResponse> items = loanRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(l -> partnerId == null || (l.getPartner() != null && partnerId.equals(l.getPartner().getId())))
                .filter(l -> normalizedReference == null || l.getLoanReference().toUpperCase(Locale.ROOT).contains(normalizedReference))
                .filter(l -> dateFrom == null || !l.getStartDate().isBefore(dateFrom))
                .filter(l -> dateTo == null || !l.getStartDate().isAfter(dateTo))
                .filter(l -> amountMin == null || l.getPrincipalAmount() >= amountMin)
                .filter(l -> amountMax == null || l.getPrincipalAmount() <= amountMax)
                .map(this::toResponse)
                .filter(r -> overdue == null || r.overdue() == overdue)
                .toList();

        return paginate(items, page, size);
    }

    public PageResponse<LoanResponse> listOverdue(Integer page, Integer size) {
        List<LoanResponse> items = loanRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toResponse)
                .filter(LoanResponse::overdue)
                .toList();
        return paginate(items, page, size);
    }

    public List<LoanInstallmentResponse> listInstallments(Long loanId) {
        getEntity(loanId);
        return installmentRepository.findByLoanIdOrderByInstallmentNumberAsc(loanId).stream()
                .map(this::toInstallmentResponse)
                .toList();
    }

    public List<LoanRepaymentResponse> listRepayments(Long loanId) {
        getEntity(loanId);
        return loanRepaymentService.listForLoan(loanId);
    }

    public LoanBalanceResponse balance(Long loanId) {
        Loan loan = getEntity(loanId);
        long totalRepaid = repaymentRepository.sumAmountByLoanId(loanId);
        long remaining = Math.max(loan.getTotalAmount() - totalRepaid, 0L);
        return new LoanBalanceResponse(loan.getId(), loan.getPrincipalAmount(), loan.getTotalAmount(), totalRepaid, remaining);
    }

    /** §22 : recalcule le solde, verifie, enregistre le remboursement du solde entier, cloture le
     * pret (et la sortie de fonds) si le solde devient nul. */
    @Transactional
    public LoanRepaymentResponse earlyRepayment(Long loanId, EarlyRepaymentRequest request) {
        LoanBalanceResponse balance = balance(loanId);
        if (balance.remainingBalance() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce pret est deja solde (Regle 8)");
        }
        return loanRepaymentService.registerRepayment(loanId, balance.remainingBalance(),
                request.paymentDate(), request.paymentMethod(), request.paymentReference(),
                StringUtils.hasText(request.comment()) ? request.comment() : "Remboursement anticipe (solde integral)");
    }

    Loan getEntity(Long id) {
        return loanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pret introuvable: " + id));
    }

    private <T> PageResponse<T> paginate(List<T> items, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);

        int totalElements = items.size();
        int totalPages = (int) Math.ceil(totalElements / (double) pageSize);
        int fromIndex = Math.min(pageNumber * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);

        return new PageResponse<>(items.subList(fromIndex, toIndex), pageNumber, pageSize, totalElements, totalPages);
    }

    private LoanResponse toResponse(Loan l) {
        long totalRepaid = repaymentRepository.sumAmountByLoanId(l.getId());
        long remaining = Math.max(l.getTotalAmount() - totalRepaid, 0L);
        boolean overdue = installmentRepository.findByLoanIdAndStatusInOrderByDueDateAsc(l.getId(),
                        List.of(InstallmentStatus.PENDING, InstallmentStatus.PARTIALLY_PAID, InstallmentStatus.OVERDUE)).stream()
                .anyMatch(i -> i.getDueDate().isBefore(LocalDate.now()));

        return new LoanResponse(
                l.getId(), l.getUuid(), l.getFundOutflow().getId(), l.getFundOutflow().getReference(), l.getFundOutflow().getStatus(),
                l.getLoanReference(), l.getPartner().getId(), l.getPartner().getCodePartenaire(),
                l.getPartner().getPrenoms() + " " + l.getPartner().getNom(),
                l.getPrincipalAmount(), l.getInterestRate(), l.getTotalAmount(), l.getCurrency(),
                l.getStartDate(), l.getExpectedEndDate(), l.getRepaymentFrequency(), l.getNumberOfInstallments(),
                remaining, overdue, l.getCreatedAt(), l.getUpdatedAt()
        );
    }

    private LoanInstallmentResponse toInstallmentResponse(LoanInstallment i) {
        long paid = i.getStatus() == InstallmentStatus.CANCELLED ? 0L : paidAmount(i);
        long remaining = Math.max(i.getTotalDue() - paid, 0L);
        long daysOverdue = i.getStatus() != InstallmentStatus.PAID && i.getStatus() != InstallmentStatus.CANCELLED
                && i.getDueDate().isBefore(LocalDate.now())
                ? java.time.temporal.ChronoUnit.DAYS.between(i.getDueDate(), LocalDate.now())
                : 0L;
        return new LoanInstallmentResponse(
                i.getId(), i.getInstallmentNumber(), i.getDueDate(), i.getPrincipalAmount(), i.getInterestAmount(),
                i.getTotalDue(), paid, remaining, i.getStatus(), daysOverdue, i.getPaidAt()
        );
    }

    private long paidAmount(LoanInstallment installment) {
        return loanRepaymentService.paidAmountForInstallment(installment.getId());
    }
}
