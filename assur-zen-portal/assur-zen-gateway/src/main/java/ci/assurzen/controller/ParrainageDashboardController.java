package ci.assurzen.controller;

import ci.assurzen.dto.ParrainageDashboardOverviewResponse;
import ci.assurzen.dto.ParrainageMensuelItem;
import ci.assurzen.dto.ParrainageParStatutItem;
import ci.assurzen.dto.ParrainageTopParrainItem;
import ci.assurzen.services.ParrainageDashboardService;
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
@RequestMapping("${env.ws}parrainage-dashboard")
@Tag(name = "Parrainage - Dashboard", description = "Indicateurs analytiques du programme de parrainage (reserve aux profils ADMIN/MARKETING/MANAGER)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ParrainageDashboardController {

    private final ParrainageDashboardService parrainageDashboardService;

    @GetMapping
    @Operation(operationId = "getParrainageDashboardOverview", summary = "Vue d'ensemble : total, taux de conversion, recompenses distribuees")
    public ResponseEntity<ParrainageDashboardOverviewResponse> overview() {
        return ResponseEntity.ok(parrainageDashboardService.overview());
    }

    @GetMapping("/par-statut")
    @Operation(operationId = "getParrainageDashboardParStatut", summary = "Repartition des parrainages par statut")
    public ResponseEntity<List<ParrainageParStatutItem>> parStatut() {
        return ResponseEntity.ok(parrainageDashboardService.parStatut());
    }

    @GetMapping("/evolution")
    @Operation(operationId = "getParrainageDashboardEvolution", summary = "Evolution mensuelle sur 12 mois (parrainages crees / convertis)")
    public ResponseEntity<List<ParrainageMensuelItem>> evolution() {
        return ResponseEntity.ok(parrainageDashboardService.evolutionMensuelle());
    }

    @GetMapping("/top-parrains")
    @Operation(operationId = "getParrainageDashboardTopParrains", summary = "Classement des parrains les plus actifs")
    public ResponseEntity<List<ParrainageTopParrainItem>> topParrains(@RequestParam(required = false, defaultValue = "10") Integer limite) {
        return ResponseEntity.ok(parrainageDashboardService.topParrains(limite));
    }
}
