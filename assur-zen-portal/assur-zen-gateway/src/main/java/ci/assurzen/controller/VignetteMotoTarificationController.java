package ci.assurzen.controller;

import ci.assurzen.dto.SimulationTarifVignetteMotoRequest;
import ci.assurzen.dto.SimulationTarifVignetteMotoResponse;
import ci.assurzen.services.DemandeVignetteMotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${env.ws}vignettes-moto/tarification")
@Tag(name = "Vignettes Moto - Tarification", description = "Simulation de tarification, sans creation de demande")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class VignetteMotoTarificationController {

    private final DemandeVignetteMotoService demandeVignetteMotoService;

    @PostMapping("/simuler")
    @Operation(operationId = "simulerTarifVignetteMoto",
            summary = "Simuler le tarif d'une vignette moto (age/cylindree/zone de livraison), sans creer de demande")
    public ResponseEntity<SimulationTarifVignetteMotoResponse> simuler(@Valid @RequestBody SimulationTarifVignetteMotoRequest request) {
        return ResponseEntity.ok(demandeVignetteMotoService.simuler(request));
    }
}
