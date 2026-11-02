package ci.assurzen.sycapay.service;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.sycapay.config.SycaPayModeTest;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.dto.SycaPayDashboardResponse;
import ci.assurzen.sycapay.dto.SycaPayStatusCountsResponse;
import ci.assurzen.sycapay.dto.SycaPayTransactionResponse;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.mapper.SycaPayMapper;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lecture seule : historique/recherche des transactions et vue synthetique du jour (section 15/16 de
 * la specification). Aucune ecriture ici - passe par SycaPayPaymentService/SycaPayStatusService.
 * requireAdmin() double le controle deja fait au niveau SecurityConfig, meme convention que le reste
 * du projet (ex. CommercialCommissionService). */
@Service
@RequiredArgsConstructor
public class SycaPayMonitoringService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Abidjan");

    private final SycaPayTransactionRepository transactionRepository;
    private final AuthService authService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SycaPayModeTest modeTest;

    public PageResponse<SycaPayTransactionResponse> search(String reference, Long commercialId, Long partenaireId, Long contractId,
                                                             SycaPayBeneficiaryType beneficiaryType, SycaPayTransactionStatus status,
                                                             Instant dateStart, Instant dateEnd, Integer page, Integer size) {
        requireAdmin();
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "requestDate"));

        Page<SycaPayTransaction> result = transactionRepository.search(
                blankToNull(reference), commercialId, partenaireId, contractId, beneficiaryType, status, dateStart, dateEnd, pageRequest);
        return new PageResponse<>(
                result.getContent().stream().map(SycaPayMapper::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** Nombre de transactions par statut pour les filtres reference/commercial/periode (memes regles que
     * search()). Tous les statuts sont presents, a 0 si absents. */
    public SycaPayStatusCountsResponse statusCounts(String reference, Long commercialId, Long partenaireId, Long contractId,
                                                    SycaPayBeneficiaryType beneficiaryType, Instant dateStart, Instant dateEnd) {
        requireAdmin();
        Map<SycaPayTransactionStatus, Long> counts = new EnumMap<>(SycaPayTransactionStatus.class);
        for (Object[] row : transactionRepository.countByStatus(blankToNull(reference), commercialId, partenaireId, contractId, beneficiaryType,
                dateStart, dateEnd)) {
            counts.put((SycaPayTransactionStatus) row[0], ((Number) row[1]).longValue());
        }
        return SycaPayStatusCountsResponse.of(counts);
    }

    public SycaPayTransactionResponse getById(Long id) {
        requireAdmin();
        return SycaPayMapper.toResponse(transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction SycaPay introuvable: " + id)));
    }

    public SycaPayDashboardResponse dashboard() {
        requireAdmin();
        Instant startOfDay = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        List<SycaPayTransaction> transactionsDuJour = transactionRepository.findByRequestDateGreaterThanEqual(startOfDay);

        long success = compte(transactionsDuJour, SycaPayTransactionStatus.SUCCESS);
        long failed = compte(transactionsDuJour, SycaPayTransactionStatus.FAILED);
        long pending = compte(transactionsDuJour, SycaPayTransactionStatus.PENDING) + compte(transactionsDuJour, SycaPayTransactionStatus.PROCESSING);
        long unknown = compte(transactionsDuJour, SycaPayTransactionStatus.UNKNOWN);
        long montantPaye = transactionsDuJour.stream()
                .filter(t -> t.getStatus() == SycaPayTransactionStatus.SUCCESS)
                .mapToLong(t -> t.getAmount() == null ? 0L : t.getAmount())
                .sum();

        boolean test = modeTest != null && modeTest.isActif();
        return new SycaPayDashboardResponse(transactionsDuJour.size(), montantPaye, success, failed, pending, unknown,
                test, test ? modeTest.getTelephone() : null, test ? modeTest.getMontant() : null);
    }

    private long compte(List<SycaPayTransaction> transactions, SycaPayTransactionStatus status) {
        return transactions.stream().filter(t -> t.getStatus() == status).count();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void requireAdmin() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (principal.roles() == null || !principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }
}
