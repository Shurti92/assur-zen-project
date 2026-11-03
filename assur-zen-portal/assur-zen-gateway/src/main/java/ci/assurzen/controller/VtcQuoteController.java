package ci.assurzen.controller;

import ci.assurzen.dto.QuoteDtos;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.MobileQuoteService;
import ci.assurzen.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Devis en ligne, assurance automobile VTC : formulaire et simulation publics, devis du client connecte (2026-11-03). */
@RestController
@RequestMapping("${env.ws}")
@Tag(name = "Devis en ligne - VTC", description = "Simulation et devis de l'assurance automobile VTC")
@RequiredArgsConstructor
public class VtcQuoteController {

    private final QuoteService quoteService;
    private final MobileQuoteService mobileQuoteService;

    @GetMapping("public/quotes/vtc/form")
    @Operation(operationId = "quoteVtcForm", summary = "Valeurs du formulaire de devis VTC (energies, durees, marques, plateformes, criteres de tarif)")
    public ResponseEntity<QuoteDtos.VtcFormInfo> formulaire() {
        return ResponseEntity.ok(quoteService.formulaireVtc());
    }

    @PostMapping("public/quotes/vtc/simulate")
    @Operation(operationId = "quoteVtcSimulate", summary = "Calcule la prime VTC decomposee et les garanties, sans rien enregistrer (aucune remise possible)")
    public ResponseEntity<QuoteDtos.VtcSimulation> simuler(@Valid @RequestBody QuoteDtos.VtcSimulationRequest request) {
        return ResponseEntity.ok(quoteService.simulerVtc(request, false));
    }

    @PostMapping("mobile/quotes/vtc")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(operationId = "mobileQuoteCreateVtc", summary = "Enregistre un devis VTC du client connecte (prix fige, aucune remise)")
    public ResponseEntity<QuoteDtos.QuoteView> creer(Authentication a, @Valid @RequestBody QuoteDtos.VtcSimulationRequest request) {
        return ResponseEntity.status(201).body(mobileQuoteService.creerVtc(((AuthenticatedUserPrincipal) a.getPrincipal()).userId(), request));
    }
}
