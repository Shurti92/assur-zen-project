package ci.assurzen.controller;

import ci.assurzen.dto.DashboardBackOfficeKpis;
import ci.assurzen.dto.DashboardKPIs;
import ci.assurzen.dto.DashboardPeriod;
import ci.assurzen.dto.ProductionHistoryEntry;
import ci.assurzen.services.DashboardBackOfficeService;
import ci.assurzen.services.DashboardService;
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
@RequestMapping("${env.ws}dashboard")
@Tag(name = "Dashboard", description = "Indicateurs agrégés")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardBackOfficeService dashboardBackOfficeService;

    @GetMapping("/kpis")
    @Operation(operationId = "getDashboardKpis", summary = "Indicateurs agrégés du commercial connecté")
    public ResponseEntity<DashboardKPIs> getDashboardKpis(@RequestParam DashboardPeriod period) {
        return ResponseEntity.ok(dashboardService.getKpis(period));
    }

    @GetMapping("/production-history")
    @Operation(operationId = "getDashboardProductionHistory",
            summary = "Historique mensuel de production de l'utilisateur connecte (partenaire ou commercial), par mois comptable, avec l'objectif du mois (months : 1 a 24, defaut 6)")
    public ResponseEntity<List<ProductionHistoryEntry>> getProductionHistory(
            @RequestParam(required = false, defaultValue = "6") int months) {
        return ResponseEntity.ok(dashboardService.getProductionHistory(months));
    }

    @GetMapping("/kpis-back-office")
    @Operation(operationId = "getDashboardKpisBackOffice", summary = "Indicateurs agrégés globaux pour le tableau de bord back-office (tous commerciaux/partenaires confondus)")
    public ResponseEntity<DashboardBackOfficeKpis> getDashboardKpisBackOffice(@RequestParam DashboardPeriod period) {
        return ResponseEntity.ok(dashboardBackOfficeService.getKpis(period));
    }
}
