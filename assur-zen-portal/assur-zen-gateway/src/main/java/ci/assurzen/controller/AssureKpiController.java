package ci.assurzen.controller;

import ci.assurzen.dto.DashboardAssureResponse;
import ci.assurzen.services.AssureKpiService;
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
@RequestMapping("${env.ws}dashboard/assures")
@Tag(name = "Dashboard - Assures", description = "KPI du dashboard assures (reserve aux administrateurs)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AssureKpiController {

    private final AssureKpiService service;

    @GetMapping
    @Operation(operationId = "getDashboardAssures",
            summary = "KPI assures sur une periode (volume/multi-equipement/repartitions/evolution), reserve aux administrateurs")
    public ResponseEntity<DashboardAssureResponse> getKpis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.getKpis(dateDebut, dateFin));
    }
}
