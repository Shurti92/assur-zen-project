package ci.assurzen.controller;

import ci.assurzen.dto.DashboardSortieFondsResponse;
import ci.assurzen.services.SortieFondsKpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("${env.ws}dashboard/sorties-fonds")
@Tag(name = "Dashboard - Sorties de fonds", description = "KPI du dashboard sorties de fonds (reserve aux administrateurs)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SortieFondsKpiController {

    private final SortieFondsKpiService service;

    @GetMapping
    @Operation(operationId = "getDashboardSortiesFonds",
            summary = "KPI sorties de fonds sur une periode (volume/financier/performance/tresorerie/repartitions/evolution), reserve aux administrateurs")
    public ResponseEntity<DashboardSortieFondsResponse> getKpis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.getKpis(dateDebut, dateFin));
    }
}
