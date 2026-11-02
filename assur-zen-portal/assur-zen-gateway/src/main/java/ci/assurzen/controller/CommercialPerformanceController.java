package ci.assurzen.controller;

import ci.assurzen.dto.CommercialBenefitPaymentResponse;
import ci.assurzen.dto.CommercialPaymentHistoryEntry;
import ci.assurzen.entities.TypeAvantageCommercial;
import ci.assurzen.services.CommercialBenefitPaymentService;
import ci.assurzen.dto.CommercialBonusPaymentRequest;
import ci.assurzen.dto.CommercialBonusPaymentResponse;
import ci.assurzen.dto.CommercialDashboardResponse;
import ci.assurzen.dto.CommercialManagementDashboardResponse;
import ci.assurzen.dto.CommercialPerformanceResponse;
import ci.assurzen.dto.CommercialPerformanceStatusRequest;
import ci.assurzen.dto.CommercialRankingEntry;
import ci.assurzen.services.CommercialBonusPaymentService;
import ci.assurzen.services.CommercialPerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Performance mensuelle, bonus et tableaux de bord (individuel et management) - §34/§35/§36/§43/§44/§70.
 * Le bonus (statut ELIGIBLE/VALIDATED/PAID) fait partie de la meme ressource que la performance
 * (CommercialPerformance, decision produit du 2026-09-07) plutot que 2 entites synchronisees - cf.
 * CommercialPerformanceService.
 */
@RestController
@RequestMapping("${env.ws}")
@Tag(name = "Performance commerciale", description = "Performance mensuelle, bonus et tableaux de bord des commerciaux")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CommercialPerformanceController {

    private final CommercialPerformanceService service;
    private final CommercialBonusPaymentService bonusPaymentService;
    private final CommercialBenefitPaymentService benefitPaymentService;

    @GetMapping("commercials/me/dashboard")
    @Operation(operationId = "getMyCommercialDashboard", summary = "Dashboard personnel 'Mon activite' (objectif/realise/taux/bonus estime du mois courant)")
    public ResponseEntity<CommercialDashboardResponse> monDashboard() {
        return ResponseEntity.ok(service.dashboard());
    }

    @GetMapping("commercials/{commercialId}/performance")
    @Operation(operationId = "listCommercialPerformance", summary = "Historique de performance mensuelle d'un commercial (soi-meme ou administrateur)")
    public ResponseEntity<List<CommercialPerformanceResponse>> list(@PathVariable Long commercialId) {
        return ResponseEntity.ok(service.list(commercialId));
    }

    @GetMapping("commercials/{commercialId}/performance/{moisComptableId}")
    @Operation(operationId = "getCommercialPerformance", summary = "Performance d'un commercial pour un mois comptable donne (recalcule en direct tant que non validee)")
    public ResponseEntity<CommercialPerformanceResponse> get(@PathVariable Long commercialId, @PathVariable Long moisComptableId) {
        return ResponseEntity.ok(service.getPerformance(commercialId, moisComptableId));
    }

    @PostMapping("commercials/{commercialId}/performance/{moisComptableId}/recalculate")
    @Operation(operationId = "recalculateCommercialPerformance", summary = "Forcer le recalcul de la performance (refuse en 409 si deja validee - reserve aux administrateurs)")
    public ResponseEntity<CommercialPerformanceResponse> recalculer(@PathVariable Long commercialId, @PathVariable Long moisComptableId) {
        return ResponseEntity.ok(service.recalculer(commercialId, moisComptableId));
    }

    @PatchMapping("commercials/{commercialId}/performance/{moisComptableId}/statut")
    @Operation(operationId = "changerStatutCommercialPerformance", summary = "Valider/cloturer la performance-bonus d'un mois (reserve aux administrateurs) - le paiement passe par POST .../bonus/pay")
    public ResponseEntity<CommercialPerformanceResponse> changerStatut(@PathVariable Long commercialId, @PathVariable Long moisComptableId, @Valid @RequestBody CommercialPerformanceStatusRequest request) {
        return ResponseEntity.ok(service.changerStatut(commercialId, moisComptableId, request.statut(), request.motif()));
    }

    @PostMapping("commercials/{commercialId}/performance/{moisComptableId}/bonus/pay")
    @Operation(operationId = "payCommercialBonus", summary = "Payer le bonus mensuel valide d'un commercial : virement Wave SycaPay reel (ou enregistrement hors application si SycaPay est desactive) - lire sycapayStatus (reserve aux administrateurs)")
    public ResponseEntity<CommercialBonusPaymentResponse> payerBonus(@PathVariable Long commercialId, @PathVariable Long moisComptableId,
                                                                     @Valid @RequestBody(required = false) CommercialBonusPaymentRequest request) {
        return ResponseEntity.ok(bonusPaymentService.payer(commercialId, moisComptableId, request));
    }

    @PostMapping("commercials/{commercialId}/payroll/{moisComptableId}/transport/pay")
    @Operation(operationId = "payCommercialTransport", summary = "Payer la prime de transport d'un commercial pour un mois (virement Wave SycaPay ou enregistrement manuel) - independant du bonus ; 409 deja paye/en cours, 422 non applicable (reserve aux administrateurs)")
    public ResponseEntity<CommercialBenefitPaymentResponse> payerTransport(@PathVariable Long commercialId, @PathVariable Long moisComptableId,
                                                                           @Valid @RequestBody(required = false) CommercialBonusPaymentRequest request) {
        return ResponseEntity.ok(benefitPaymentService.payer(commercialId, moisComptableId, TypeAvantageCommercial.TRANSPORT, request));
    }

    @PostMapping("commercials/{commercialId}/payroll/{moisComptableId}/communication/pay")
    @Operation(operationId = "payCommercialCommunication", summary = "Payer l'indemnite de communication d'un commercial pour un mois (virement Wave SycaPay ou enregistrement manuel) - independant du bonus ; 409 deja paye/en cours, 422 non applicable (reserve aux administrateurs)")
    public ResponseEntity<CommercialBenefitPaymentResponse> payerCommunication(@PathVariable Long commercialId, @PathVariable Long moisComptableId,
                                                                               @Valid @RequestBody(required = false) CommercialBonusPaymentRequest request) {
        return ResponseEntity.ok(benefitPaymentService.payer(commercialId, moisComptableId, TypeAvantageCommercial.COMMUNICATION, request));
    }

    @GetMapping("commercial-performance/payments")
    @Operation(operationId = "listCommercialPayments", summary = "Historique unifie des paiements commerciaux (champ type = BONUS | TRANSPORT | COMMUNICATION), filtrable par commercial et par type (reserve aux administrateurs)")
    public ResponseEntity<List<CommercialPaymentHistoryEntry>> historiquePaiements(@RequestParam(required = false) Long commercialId,
                                                                                   @RequestParam(required = false) String type) {
        return ResponseEntity.ok(benefitPaymentService.historique(commercialId, type));
    }

    @GetMapping("commercial-performance/bonus-payments")
    @Operation(operationId = "listCommercialBonusPayments", summary = "Historique des paiements de bonus (payes, en cours, echoues), filtrable par commercial (reserve aux administrateurs)")
    public ResponseEntity<List<CommercialPerformanceResponse>> historiqueBonus(@RequestParam(required = false) Long commercialId) {
        return ResponseEntity.ok(bonusPaymentService.historique(commercialId));
    }

    @GetMapping("commercial-performance/ranking")
    @Operation(operationId = "getCommercialRanking", summary = "Classement des commerciaux par taux d'atteinte (§36 - jamais de montant salarial)")
    public ResponseEntity<List<CommercialRankingEntry>> ranking(@RequestParam(required = false) Long moisComptableId) {
        return ResponseEntity.ok(service.ranking(moisComptableId));
    }

    @GetMapping("commercial-performance/dashboard")
    @Operation(operationId = "getCommercialManagementDashboard", summary = "Dashboard management : vue globale de la performance de l'equipe commerciale (reserve aux administrateurs)")
    public ResponseEntity<CommercialManagementDashboardResponse> managementDashboard(@RequestParam(required = false) Long moisComptableId) {
        return ResponseEntity.ok(service.managementDashboard(moisComptableId));
    }

    @GetMapping("commercial-performance/export")
    @Operation(operationId = "exportCommercialPerformance", summary = "Exporter performance/bonus/remuneration au format CSV, filtrable par commercial et/ou mois comptable (reserve aux administrateurs)")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long commercialId, @RequestParam(required = false) Long moisComptableId) {
        byte[] csv = service.exportCsv(commercialId, moisComptableId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("commercial-performance.csv").build().toString())
                .body(csv);
    }
}
