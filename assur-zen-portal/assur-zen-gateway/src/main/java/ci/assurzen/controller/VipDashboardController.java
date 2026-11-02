package ci.assurzen.controller;

import ci.assurzen.dto.VipMensuelItem;
import ci.assurzen.dto.VipOverviewResponse;
import ci.assurzen.dto.VipParDimensionItem;
import ci.assurzen.dto.VipParNiveauItem;
import ci.assurzen.dto.VipTopClientItem;
import ci.assurzen.services.VipDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}vip-dashboard")
@Tag(name = "Dashboard Clients VIP", description = "Analytique lecture-seule sur les comptes VIP (ADMIN/MARKETING/MANAGER)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class VipDashboardController {

    private final VipDashboardService vipDashboardService;

    @GetMapping("/overview")
    @Operation(operationId = "getVipDashboardOverview", summary = "KPIs generaux (nombre de VIP, score moyen, prime generee, recompenses distribuees)")
    public ResponseEntity<VipOverviewResponse> overview() {
        return ResponseEntity.ok(vipDashboardService.overview());
    }

    @GetMapping("/par-niveau")
    @Operation(operationId = "getVipDashboardParNiveau", summary = "Repartition des comptes par palier de fidelite")
    public ResponseEntity<List<VipParNiveauItem>> parNiveau() {
        return ResponseEntity.ok(vipDashboardService.parNiveau());
    }

    @GetMapping("/mensuel")
    @Operation(operationId = "getVipDashboardMensuel", summary = "Evolution des entrees/sorties VIP sur les 12 derniers mois")
    public ResponseEntity<List<VipMensuelItem>> mensuel() {
        return ResponseEntity.ok(vipDashboardService.mensuel());
    }

    @GetMapping("/top-clients")
    @Operation(operationId = "getVipDashboardTopClients", summary = "Top clients VIP par score de fidelite")
    public ResponseEntity<List<VipTopClientItem>> topClients(@RequestParam(required = false, defaultValue = "10") Integer limite) {
        return ResponseEntity.ok(vipDashboardService.topClients(limite));
    }

    @GetMapping("/par-produit")
    @Operation(operationId = "getVipDashboardParProduit", summary = "Prime generee par produit parmi les clients VIP")
    public ResponseEntity<List<VipParDimensionItem>> parProduit() {
        return ResponseEntity.ok(vipDashboardService.parProduit());
    }

    @GetMapping("/par-gestionnaire")
    @Operation(operationId = "getVipDashboardParGestionnaire", summary = "Prime generee par gestionnaire/commercial parmi les clients VIP")
    public ResponseEntity<List<VipParDimensionItem>> parGestionnaire() {
        return ResponseEntity.ok(vipDashboardService.parGestionnaire());
    }

    @GetMapping("/par-courtier")
    @Operation(operationId = "getVipDashboardParCourtier", summary = "Prime generee par courtier/partenaire parmi les clients VIP")
    public ResponseEntity<List<VipParDimensionItem>> parCourtier() {
        return ResponseEntity.ok(vipDashboardService.parCourtier());
    }
}
