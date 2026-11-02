package ci.assurzen.controller;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.sycapay.dto.SycaPayBalanceResponse;
import ci.assurzen.sycapay.dto.SycaPayDashboardResponse;
import ci.assurzen.sycapay.dto.SycaPayStatusCountsResponse;
import ci.assurzen.sycapay.dto.SycaPayTransactionResponse;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.mapper.SycaPayMapper;
import ci.assurzen.sycapay.service.SycaPayBalanceService;
import ci.assurzen.sycapay.service.SycaPayMonitoringService;
import ci.assurzen.sycapay.service.SycaPayStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Monitoring et operations SycaPay (solde, historique/recherche des transactions, reconciliation
 * manuelle). Reserve aux administrateurs (SecurityConfig : /api-assur-zen/sycapay/** en
 * hasRole(ADMIN), meme convention que /commissions/**) - aucune donnee sensible (apiKey/secretKey/
 * token) n'est jamais exposee par ces endpoints, elles n'existent meme pas sur les DTO retournes.
 *
 * Le declenchement d'un paiement ne passe volontairement PAS par ce controller : il reste integre a
 * POST /commercial-commissions/pay (CommercialCommissionController), qui est le seul point d'entree
 * metier legitime pour payer une commission (decision produit du 2026-09-22).
 */
@RestController
@RequestMapping("${env.ws}sycapay")
@Tag(name = "SycaPay", description = "Solde marchand, historique et reconciliation des virements Wave (paiement des commissions commerciales)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SycaPayController {

    private final SycaPayBalanceService balanceService;
    private final SycaPayMonitoringService monitoringService;
    private final SycaPayStatusService statusService;

    @GetMapping("/balance")
    @Operation(operationId = "getSycaPayBalance",
            summary = "Consulter le solde marchand SycaPay (mis en cache ~20s pour le rafraichissement regulier du frontend, "
                    + "refresh=true pour forcer un appel direct)")
    public ResponseEntity<SycaPayBalanceResponse> balance(@RequestParam(required = false, defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(balanceService.checkBalance(refresh));
    }

    @GetMapping("/dashboard")
    @Operation(operationId = "getSycaPayDashboard", summary = "Vue synthetique du jour (paiements/montant/statuts)")
    public ResponseEntity<SycaPayDashboardResponse> dashboard() {
        return ResponseEntity.ok(monitoringService.dashboard());
    }

    @GetMapping("/transactions")
    @Operation(operationId = "searchSycaPayTransactions",
            summary = "Rechercher l'historique des transactions SycaPay (reference, beneficiaire commercial/partenaire, contrat, statut, periode)")
    public ResponseEntity<PageResponse<SycaPayTransactionResponse>> search(
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) Long commercialId,
            @RequestParam(required = false) Long partenaireId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) SycaPayBeneficiaryType beneficiaryType,
            @RequestParam(required = false) SycaPayTransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateEnd,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(monitoringService.search(reference, commercialId, partenaireId, contractId, beneficiaryType, status,
                dateStart, dateEnd, page, size));
    }

    @GetMapping("/transactions/status-counts")
    @Operation(operationId = "countSycaPayTransactionsByStatus",
            summary = "Nombre de transactions SycaPay par statut (memes filtres que la recherche, sans filtre de statut) - "
                    + "les 6 statuts sont toujours presents, total = somme")
    public ResponseEntity<SycaPayStatusCountsResponse> statusCounts(
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) Long commercialId,
            @RequestParam(required = false) Long partenaireId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) SycaPayBeneficiaryType beneficiaryType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateEnd) {
        return ResponseEntity.ok(monitoringService.statusCounts(reference, commercialId, partenaireId, contractId, beneficiaryType, dateStart, dateEnd));
    }

    @GetMapping("/transactions/{id}")
    @Operation(operationId = "getSycaPayTransaction", summary = "Detail d'une transaction SycaPay")
    public ResponseEntity<SycaPayTransactionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getById(id));
    }

    @PostMapping("/transactions/{id}/reconcile")
    @Operation(operationId = "reconcileSycaPayTransaction",
            summary = "Reconcilier une transaction PROCESSING/UNKNOWN aupres de SycaPay (getStatusByOrderID)")
    public ResponseEntity<SycaPayTransactionResponse> reconcile(@PathVariable Long id) {
        SycaPayTransaction updated = statusService.reconcile(id);
        return ResponseEntity.ok(SycaPayMapper.toResponse(updated));
    }
}
