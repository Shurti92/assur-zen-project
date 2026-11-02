package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.CreateFundOutflowRequest;
import ci.assurzen.dto.DisburseFundOutflowRequest;
import ci.assurzen.dto.FundOutflowDisbursementResponse;
import ci.assurzen.dto.FundOutflowResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.UpdateFundOutflowRequest;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.DisbursementStatus;
import ci.assurzen.entities.FundOutflow;
import ci.assurzen.entities.FundOutflowDisbursement;
import ci.assurzen.entities.FundOutflowPriority;
import ci.assurzen.entities.FundOutflowStatus;
import ci.assurzen.entities.FundOutflowType;
import ci.assurzen.entities.Loan;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.FundOutflowDisbursementRepository;
import ci.assurzen.repositories.FundOutflowRepository;
import ci.assurzen.repositories.LoanRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Moteur generique des sorties de fonds (Phase 1). Une seule etape de validation ADMIN (decision de
 * cadrage confirmee) : le workflow riche (SUBMIT/REVIEW/APPROVE/AUTHORIZE/DISBURSE) reste present
 * pour la tracabilite et l'UI, mais aucune separation des taches n'est appliquee techniquement (ce
 * projet n'a qu'un seul role privilegie).
 */
@Service
@RequiredArgsConstructor
public class FundOutflowService {

    private static final long DEDUPLICATION_WINDOW_SECONDS = 10L;

    private final FundOutflowRepository fundOutflowRepository;
    private final FundOutflowDisbursementRepository disbursementRepository;
    private final LoanRepository loanRepository;
    private final FundOutflowTypeService fundOutflowTypeService;
    private final PartenaireRepository partenaireRepository;
    private final DisbursementService disbursementService;
    private final LoanService loanService;
    private final SmsNotificationService smsNotificationService;
    private final CodeGeneratorService codeGeneratorService;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;

    // ---------------------------------------------------------------- Creation / consultation

    @Transactional
    public FundOutflowResponse create(CreateFundOutflowRequest request) {
        FundOutflowType type = fundOutflowTypeService.getEntity(request.outflowTypeId());
        boolean isLoan = fundOutflowTypeService.isLoanType(type);
        Partenaire partner = isLoan
                ? requireValidPartnerBeneficiary(request.beneficiaryType(), request.beneficiaryId())
                : resolveBeneficiary(request.beneficiaryType(), request.beneficiaryId());
        // Conditions du pret (taux/dates/frequence/nombre d'echeances) volontairement optionnelles a
        // la creation - completees plus tard via PUT tant que le statut reste DRAFT, mais exigees au
        // moment du decaissement par LoanService.createFromDisbursedOutflow (impossible de generer un
        // echeancier sans elles).

        String username = authService.currentPrincipal().username();
        FundOutflow outflow = FundOutflow.builder()
                .uuid(UUID.randomUUID().toString())
                .reference(codeGeneratorService.generate(CodePrefix.FUND_OUTFLOW))
                .outflowType(type)
                .beneficiaryType(StringUtils.hasText(request.beneficiaryType()) ? request.beneficiaryType().toUpperCase(Locale.ROOT) : null)
                .beneficiaryId(request.beneficiaryId())
                .beneficiaryLabel(request.beneficiaryLabel())
                .amount(request.amount())
                .currency(StringUtils.hasText(request.currency()) ? request.currency() : "XOF")
                .description(request.description())
                .reason(request.reason())
                .status(FundOutflowStatus.DRAFT)
                .priority(request.priority() != null ? request.priority() : FundOutflowPriority.NORMAL)
                .dueDate(request.dueDate())
                .loanInterestRate(request.loanInterestRate())
                .loanStartDate(request.loanStartDate())
                .loanExpectedEndDate(request.loanExpectedEndDate())
                .loanRepaymentFrequency(request.loanRepaymentFrequency())
                .loanNumberOfInstallments(request.loanNumberOfInstallments())
                .build();
        outflow.setCreatedBy(username);
        outflow.setUpdatedBy(username);
        outflow = fundOutflowRepository.save(outflow);

        functionalAuditRecorder.recordAction("SORTIES_FONDS", "FundOutflow", outflow.getReference(), ActionAudit.CREATE,
                "Sortie de fonds creee (" + type.getCode() + ", " + outflow.getAmount() + " " + outflow.getCurrency() + ")");

        if (isLoan && partner != null) {
            smsNotificationService.sendLoanCreatedSms(partner.getTelephone(),
                    partner.getPrenoms() + " " + partner.getNom(), outflow.getReference(), outflow.getAmount(), outflow.getCurrency());
        }
        return toResponse(outflow);
    }

    public FundOutflowResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public PageResponse<FundOutflowResponse> search(String reference, FundOutflowStatus status, Long outflowTypeId,
                                                      String beneficiaryType, Long beneficiaryId,
                                                      Long amountMin, Long amountMax, LocalDate dateFrom, LocalDate dateTo,
                                                      FundOutflowPriority priority, Integer page, Integer size) {
        return paginate(filter(reference, status, outflowTypeId, beneficiaryType, beneficiaryId, amountMin, amountMax, dateFrom, dateTo, priority), page, size);
    }

    public PageResponse<FundOutflowResponse> listPendingApprovals(Integer page, Integer size) {
        return paginate(filter(null, FundOutflowStatus.UNDER_REVIEW, null, null, null, null, null, null, null, null), page, size);
    }

    @Transactional
    public FundOutflowResponse update(Long id, UpdateFundOutflowRequest request) {
        FundOutflow outflow = getEntity(id);
        if (outflow.getStatus() != FundOutflowStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une sortie de fonds ne se modifie qu'a l'etat DRAFT (statut actuel: " + outflow.getStatus() + ")");
        }
        FundOutflowType type = fundOutflowTypeService.getEntity(request.outflowTypeId());
        if (fundOutflowTypeService.isLoanType(type)) {
            requireValidPartnerBeneficiary(request.beneficiaryType(), request.beneficiaryId());
        } else {
            resolveBeneficiary(request.beneficiaryType(), request.beneficiaryId());
        }

        outflow.setOutflowType(type);
        outflow.setBeneficiaryType(StringUtils.hasText(request.beneficiaryType()) ? request.beneficiaryType().toUpperCase(Locale.ROOT) : null);
        outflow.setBeneficiaryId(request.beneficiaryId());
        outflow.setBeneficiaryLabel(request.beneficiaryLabel());
        outflow.setAmount(request.amount());
        if (StringUtils.hasText(request.currency())) {
            outflow.setCurrency(request.currency());
        }
        outflow.setDescription(request.description());
        outflow.setReason(request.reason());
        if (request.priority() != null) {
            outflow.setPriority(request.priority());
        }
        outflow.setDueDate(request.dueDate());
        outflow.setLoanInterestRate(request.loanInterestRate());
        outflow.setLoanStartDate(request.loanStartDate());
        outflow.setLoanExpectedEndDate(request.loanExpectedEndDate());
        outflow.setLoanRepaymentFrequency(request.loanRepaymentFrequency());
        outflow.setLoanNumberOfInstallments(request.loanNumberOfInstallments());
        outflow.setUpdatedBy(authService.currentPrincipal().username());

        return toResponse(fundOutflowRepository.save(outflow));
    }

    @Transactional
    public void delete(Long id) {
        FundOutflow outflow = getEntity(id);
        if (outflow.getStatus() != FundOutflowStatus.DRAFT && outflow.getStatus() != FundOutflowStatus.CANCELLED
                && outflow.getStatus() != FundOutflowStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer une sortie de fonds au statut " + outflow.getStatus() + " (Regle 7)");
        }
        outflow.setDeleted(true);
        fundOutflowRepository.save(outflow);
        functionalAuditRecorder.recordAction("SORTIES_FONDS", "FundOutflow", outflow.getReference(), ActionAudit.DELETE, null);
    }

    // ---------------------------------------------------------------- Transitions

    @Transactional
    public FundOutflowResponse submit(Long id) {
        FundOutflow outflow = requireStatus(id, FundOutflowStatus.DRAFT);
        outflow.setStatus(FundOutflowStatus.SUBMITTED);
        outflow.setSubmittedAt(Instant.now());
        outflow.setSubmittedBy(currentUsername());
        outflow.setUpdatedBy(currentUsername());
        fundOutflowRepository.save(outflow);
        functionalAuditRecorder.recordAction("SORTIES_FONDS", "FundOutflow", outflow.getReference(), ActionAudit.SUBMIT, null);
        return toResponse(outflow);
    }

    @Transactional
    public FundOutflowResponse review(Long id) {
        FundOutflow outflow = requireStatus(id, FundOutflowStatus.SUBMITTED);
        outflow.setStatus(FundOutflowStatus.UNDER_REVIEW);
        outflow.setUpdatedBy(currentUsername());
        fundOutflowRepository.save(outflow);
        functionalAuditRecorder.recordAction("SORTIES_FONDS", "FundOutflow", outflow.getReference(), ActionAudit.REVIEW, null);
        return toResponse(outflow);
    }

    @Transactional
    public FundOutflowResponse approve(Long id) {
        FundOutflow outflow = requireStatus(id, FundOutflowStatus.UNDER_REVIEW);
        outflow.setStatus(FundOutflowStatus.APPROVED);
        outflow.setApprovedAt(Instant.now());
        outflow.setApprovedBy(currentUsername());
        outflow.setUpdatedBy(currentUsername());
        fundOutflowRepository.save(outflow);
        functionalAuditRecorder.recordAction("SORTIES_FONDS", "FundOutflow", outflow.getReference(), ActionAudit.APPROVE, null);
        return toResponse(outflow);
    }

    @Transactional
    public FundOutflowResponse reject(Long id, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le motif de rejet est obligatoire");
        }
        FundOutflow outflow = requireStatus(id, FundOutflowStatus.UNDER_REVIEW);
        outflow.setStatus(FundOutflowStatus.REJECTED);
        outflow.setRejectionReason(reason);
        outflow.setUpdatedBy(currentUsername());
        fundOutflowRepository.save(outflow);
        functionalAuditRecorder.recordAction("SORTIES_FONDS", "FundOutflow", outflow.getReference(), ActionAudit.REJECT, reason);
        return toResponse(outflow);
    }

    @Transactional
    public FundOutflowResponse authorize(Long id) {
        FundOutflow outflow = requireStatus(id, FundOutflowStatus.APPROVED);
        outflow.setStatus(FundOutflowStatus.AUTHORIZED);
        outflow.setAuthorizedAt(Instant.now());
        outflow.setAuthorizedBy(currentUsername());
        outflow.setUpdatedBy(currentUsername());
        fundOutflowRepository.save(outflow);
        functionalAuditRecorder.recordAction("SORTIES_FONDS", "FundOutflow", outflow.getReference(), ActionAudit.AUTHORIZE, null);
        return toResponse(outflow);
    }

    @Transactional
    public FundOutflowResponse cancel(Long id, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le motif d'annulation est obligatoire");
        }
        FundOutflow outflow = getEntity(id);
        if (outflow.getStatus() != FundOutflowStatus.DRAFT && outflow.getStatus() != FundOutflowStatus.SUBMITTED
                && outflow.getStatus() != FundOutflowStatus.UNDER_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une sortie de fonds ne peut plus etre annulee au statut " + outflow.getStatus());
        }
        outflow.setStatus(FundOutflowStatus.CANCELLED);
        outflow.setRejectionReason(reason);
        outflow.setUpdatedBy(currentUsername());
        fundOutflowRepository.save(outflow);
        functionalAuditRecorder.recordAction("SORTIES_FONDS", "FundOutflow", outflow.getReference(), ActionAudit.CANCEL, reason);
        return toResponse(outflow);
    }

    @Transactional
    public FundOutflowResponse close(Long id) {
        FundOutflow outflow = getEntity(id);
        boolean closable = outflow.getStatus() == FundOutflowStatus.DISBURSED
                || outflow.getStatus() == FundOutflowStatus.REPAID
                || outflow.getStatus() == FundOutflowStatus.CANCELLED
                || outflow.getStatus() == FundOutflowStatus.REJECTED;
        if (!closable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une sortie de fonds au statut " + outflow.getStatus() + " ne peut pas etre cloturee");
        }
        outflow.setStatus(FundOutflowStatus.CLOSED);
        outflow.setClosedAt(Instant.now());
        outflow.setClosedBy(currentUsername());
        outflow.setUpdatedBy(currentUsername());
        fundOutflowRepository.save(outflow);
        functionalAuditRecorder.recordAction("SORTIES_FONDS", "FundOutflow", outflow.getReference(), ActionAudit.CLOSE, null);
        return toResponse(outflow);
    }

    /**
     * Decaissement (§23/§26). Idempotence : meme patron exact que
     * CommissionPartialPaymentService.demanderPaiementPartiel - verrou pessimiste sur TOUTES les
     * lignes de decaissement de cette sortie, en toute premiere instruction de la transaction (avant
     * meme requireStatus), cle client prioritaire sur la fenetre de repli de 10s.
     */
    @Transactional
    public FundOutflowDisbursementResponse disburse(Long id, DisburseFundOutflowRequest request, String idempotencyKey) {
        disbursementRepository.lockToutesPourSortie(id);

        if (StringUtils.hasText(idempotencyKey)) {
            Optional<FundOutflowDisbursement> existant = disbursementRepository.findByFundOutflowIdAndIdempotencyKey(id, idempotencyKey);
            if (existant.isPresent()) {
                if (!existant.get().getMethod().equals(request.method())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Cle d'idempotence deja utilisee pour un moyen de paiement different");
                }
                return toDisbursementResponse(existant.get());
            }
        } else {
            Instant depuis = Instant.now().minusSeconds(DEDUPLICATION_WINDOW_SECONDS);
            Optional<FundOutflowDisbursement> doublon = disbursementRepository.findFirstByFundOutflowIdOrderByCreatedAtDesc(id)
                    .filter(d -> d.getCreatedAt().isAfter(depuis));
            if (doublon.isPresent()) {
                return toDisbursementResponse(doublon.get());
            }
        }

        FundOutflow outflow = requireStatus(id, FundOutflowStatus.AUTHORIZED);
        String username = currentUsername();

        FundOutflowDisbursement disbursement = FundOutflowDisbursement.builder()
                .fundOutflow(outflow)
                .reference(codeGeneratorService.generate(CodePrefix.DISBURSEMENT))
                .amount(outflow.getAmount())
                .currency(outflow.getCurrency())
                .method(request.method())
                .paymentReference(request.paymentReference())
                .status(DisbursementStatus.PROCESSING)
                .idempotencyKey(idempotencyKey)
                .processedBy(username)
                .disbursementDate(LocalDate.now(ZoneId.of("Africa/Abidjan")))
                .build();
        disbursement = disbursementService.process(disbursement);
        disbursement = disbursementRepository.save(disbursement);

        if (disbursement.getStatus() == DisbursementStatus.SUCCESS) {
            outflow.setStatus(FundOutflowStatus.DISBURSED);
            outflow.setDisbursedAt(Instant.now());
            outflow.setDisbursedBy(username);
            outflow.setUpdatedBy(username);
            fundOutflowRepository.save(outflow);

            if (fundOutflowTypeService.isLoanType(outflow.getOutflowType())) {
                loanService.createFromDisbursedOutflow(outflow);
            }
        }

        // entityName="FundOutflow" + entityId=reference, comme SUBMIT/REVIEW/APPROVE/REJECT/
        // AUTHORIZE/CANCEL/CLOSE ci-dessus - sinon cette action n'apparait jamais dans
        // GET /fund-outflows/{id}/history (qui filtre precisement sur ce couple). Bug reel trouve en
        // integration par l'equipe frontend le 2026-09-03 (entityName="FundOutflowDisbursement" ne
        // matchait jamais le filtre).
        functionalAuditRecorder.recordAction("SORTIES_FONDS", "FundOutflow", outflow.getReference(),
                ActionAudit.DISBURSE, "Decaissement " + disbursement.getStatus() + " (" + disbursement.getReference() + ")");
        return toDisbursementResponse(disbursement);
    }

    // ---------------------------------------------------------------- Internes

    FundOutflow getEntity(Long id) {
        return fundOutflowRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sortie de fonds introuvable: " + id));
    }

    private FundOutflow requireStatus(Long id, FundOutflowStatus expected) {
        FundOutflow outflow = getEntity(id);
        if (outflow.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transition impossible depuis le statut " + outflow.getStatus() + " (attendu: " + expected + ")");
        }
        return outflow;
    }

    private String currentUsername() {
        return authService.currentPrincipal().username();
    }

    /** Un pret doit toujours avoir un partenaire beneficiaire reel (Regle 4) - obligatoire des la
     * creation, contrairement aux conditions du pret (taux/echeances) qui restent optionnelles a ce
     * stade. 404 si le partenaire n'existe pas en base. */
    private Partenaire requireValidPartnerBeneficiary(String beneficiaryType, Long beneficiaryId) {
        if (!StringUtils.hasText(beneficiaryType) || !"PARTNER".equalsIgnoreCase(beneficiaryType) || beneficiaryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un pret doit avoir un partenaire beneficiaire valide (Regle 4)");
        }
        return partenaireRepository.findById(beneficiaryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + beneficiaryId));
    }

    private Partenaire resolveBeneficiary(String beneficiaryType, Long beneficiaryId) {
        if (StringUtils.hasText(beneficiaryType) && "PARTNER".equalsIgnoreCase(beneficiaryType) && beneficiaryId != null) {
            return partenaireRepository.findById(beneficiaryId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partenaire introuvable: " + beneficiaryId));
        }
        return null;
    }

    private List<FundOutflowResponse> filter(String reference, FundOutflowStatus status, Long outflowTypeId,
                                              String beneficiaryType, Long beneficiaryId,
                                              Long amountMin, Long amountMax, LocalDate dateFrom, LocalDate dateTo,
                                              FundOutflowPriority priority) {
        String normalizedReference = StringUtils.hasText(reference) ? reference.trim().toUpperCase(Locale.ROOT) : null;
        String normalizedBeneficiaryType = StringUtils.hasText(beneficiaryType) ? beneficiaryType.trim().toUpperCase(Locale.ROOT) : null;
        Instant from = dateFrom != null ? dateFrom.atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
        Instant to = dateTo != null ? dateTo.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : null;

        return fundOutflowRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(o -> normalizedReference == null || o.getReference().toUpperCase(Locale.ROOT).contains(normalizedReference))
                .filter(o -> status == null || o.getStatus() == status)
                .filter(o -> outflowTypeId == null || outflowTypeId.equals(o.getOutflowType().getId()))
                .filter(o -> normalizedBeneficiaryType == null || normalizedBeneficiaryType.equals(o.getBeneficiaryType()))
                .filter(o -> beneficiaryId == null || beneficiaryId.equals(o.getBeneficiaryId()))
                .filter(o -> amountMin == null || o.getAmount() >= amountMin)
                .filter(o -> amountMax == null || o.getAmount() <= amountMax)
                .filter(o -> from == null || !o.getCreatedAt().isBefore(from))
                .filter(o -> to == null || o.getCreatedAt().isBefore(to))
                .filter(o -> priority == null || o.getPriority() == priority)
                .map(this::toResponse)
                .toList();
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

    private FundOutflowResponse toResponse(FundOutflow o) {
        Long loanId = loanRepository.findByFundOutflowId(o.getId()).map(Loan::getId).orElse(null);
        return new FundOutflowResponse(
                o.getId(), o.getUuid(), o.getReference(),
                o.getOutflowType().getId(), o.getOutflowType().getCode(), o.getOutflowType().getNom(),
                o.getBeneficiaryType(), o.getBeneficiaryId(), o.getBeneficiaryLabel(),
                o.getAmount(), o.getCurrency(), o.getDescription(), o.getReason(),
                o.getStatus(), o.getPriority(), o.getDueDate(), o.getRejectionReason(), loanId,
                o.getLoanInterestRate(), o.getLoanStartDate(), o.getLoanExpectedEndDate(),
                o.getLoanRepaymentFrequency(), o.getLoanNumberOfInstallments(),
                o.getSubmittedAt(), o.getSubmittedBy(),
                o.getApprovedAt(), o.getApprovedBy(),
                o.getAuthorizedAt(), o.getAuthorizedBy(),
                o.getDisbursedAt(), o.getDisbursedBy(),
                o.getClosedAt(), o.getClosedBy(),
                o.getCreatedAt(), o.getCreatedBy(), o.getUpdatedAt(), o.getUpdatedBy()
        );
    }

    private FundOutflowDisbursementResponse toDisbursementResponse(FundOutflowDisbursement d) {
        return new FundOutflowDisbursementResponse(
                d.getId(), d.getFundOutflow().getId(), d.getReference(), d.getAmount(), d.getCurrency(),
                d.getMethod(), d.getPaymentReference(), d.getStatus(), d.getProcessedBy(), d.getDisbursementDate(), d.getCreatedAt()
        );
    }
}
