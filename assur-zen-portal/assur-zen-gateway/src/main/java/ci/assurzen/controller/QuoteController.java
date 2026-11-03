package ci.assurzen.controller;

import ci.assurzen.dto.QuoteDtos;
import ci.assurzen.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Devis en ligne, lot 1 : simulation Moto sans compte (2026-11-03). */
@RestController
@RequestMapping("${env.ws}public/quotes/moto")
@Tag(name = "Devis en ligne - Moto", description = "Simulation de la prime d'assurance moto (public, sans compte)")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    @GetMapping("/form")
    @Operation(operationId = "quoteMotoForm", summary = "Valeurs proposees par le formulaire de devis moto (types, durees, formule, date minimale d'effet)")
    public ResponseEntity<QuoteDtos.MotoFormInfo> formulaire() {
        return ResponseEntity.ok(quoteService.formulaireMoto());
    }

    @PostMapping("/simulate")
    @Operation(operationId = "quoteMotoSimulate", summary = "Calcule la prime moto (decomposee) et les garanties, sans rien enregistrer")
    public ResponseEntity<QuoteDtos.MotoSimulation> simuler(@Valid @RequestBody QuoteDtos.MotoSimulationRequest request) {
        return ResponseEntity.ok(quoteService.simulerMoto(request));
    }
}
