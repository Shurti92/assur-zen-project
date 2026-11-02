package ci.assurzen.controller;

import ci.assurzen.dto.DashboardVignetteResponse;
import ci.assurzen.services.VignetteKpiService;
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
@RequestMapping("${env.ws}dashboard/vignettes")
@Tag(name = "Dashboard - Vignettes", description = "KPI du dashboard vignettes moto (reserve aux administrateurs)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class VignetteKpiController {

    private final VignetteKpiService service;

    @GetMapping
    @Operation(operationId = "getDashboardVignettes",
            summary = "KPI vignettes moto sur une periode (volume/financier/performance/repartitions/evolution), reserve aux administrateurs")
    public ResponseEntity<DashboardVignetteResponse> getKpis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.getKpis(dateDebut, dateFin));
    }
}
