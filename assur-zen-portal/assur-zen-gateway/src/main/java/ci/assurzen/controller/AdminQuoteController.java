package ci.assurzen.controller;

import ci.assurzen.dto.QuoteDtos;
import ci.assurzen.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Grille tarifaire des devis en ligne, reservee aux administrateurs (2026-11-03). */
@RestController
@RequestMapping("${env.ws}admin/quotes/tariffs")
@Tag(name = "Administration - Devis", description = "Grille tarifaire des devis en ligne (reserve aux administrateurs)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AdminQuoteController {

    private final QuoteService quoteService;

    @GetMapping
    @Operation(operationId = "adminQuoteTariffs", summary = "Liste la grille tarifaire")
    public ResponseEntity<QuoteDtos.TariffList> lister() {
        return ResponseEntity.ok(quoteService.listerGrille());
    }

    @PutMapping
    @Operation(operationId = "adminQuoteTariffsSave", summary = "Cree ou met a jour des lignes de la grille ; le total doit egaler la somme des composantes")
    public ResponseEntity<QuoteDtos.TariffList> enregistrer(@Valid @RequestBody QuoteDtos.TariffUpdate update) {
        return ResponseEntity.ok(quoteService.enregistrerGrille(update));
    }
}
