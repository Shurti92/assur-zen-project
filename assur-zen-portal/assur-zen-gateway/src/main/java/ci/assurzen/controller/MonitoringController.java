package ci.assurzen.controller;

import ci.assurzen.dto.MonitoringHealthResponse;
import ci.assurzen.dto.MonitoringMetricsResponse;
import ci.assurzen.services.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${env.ws}monitoring")
@Tag(name = "Monitoring", description = "Etat technique et indicateurs metier pour le dashboard de supervision")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;

    @GetMapping("/health")
    @Operation(operationId = "getMonitoringHealth", summary = "Etat technique (Gateway, Auth, Paiement, Sinistre)")
    public ResponseEntity<MonitoringHealthResponse> getHealth() {
        return ResponseEntity.ok(monitoringService.getHealth());
    }

    @GetMapping("/metrics")
    @Operation(operationId = "getMonitoringMetrics", summary = "Indicateurs metier (contrats, paiements, sinistres, connexions, delais, erreurs)")
    public ResponseEntity<MonitoringMetricsResponse> getMetrics() {
        return ResponseEntity.ok(monitoringService.getMetrics());
    }
}
