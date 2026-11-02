package ci.assurzen.controller;

import ci.assurzen.dto.DashboardGlobalResponse;
import ci.assurzen.services.DashboardGlobalService;
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
@RequestMapping("${env.ws}dashboard/global")
@Tag(name = "Dashboard - Global", description = "Vue de synthese assures/vignettes/sorties de fonds (reserve aux administrateurs)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DashboardGlobalController {

    private final DashboardGlobalService service;

    @GetMapping
    @Operation(operationId = "getDashboardGlobal",
            summary = "Vue de synthese des KPI assures/vignettes/sorties de fonds sur une periode, reserve aux administrateurs")
    public ResponseEntity<DashboardGlobalResponse> getKpis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.getKpis(dateDebut, dateFin));
    }
}
