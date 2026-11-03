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

/** Marques proposees dans le formulaire du devis moto, reservees aux administrateurs (2026-11-03). */
@RestController
@RequestMapping("${env.ws}admin/quotes/moto/brands")
@Tag(name = "Administration - Devis moto : marques", description = "Liste deroulante des marques du devis moto (reserve aux administrateurs)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AdminQuoteBrandController {

    private final QuoteService quoteService;

    @GetMapping
    @Operation(operationId = "adminQuoteBrands", summary = "Marques proposees, dans l'ordre d'affichage")
    public ResponseEntity<QuoteDtos.BrandList> lister() {
        return ResponseEntity.ok(quoteService.listerMarques());
    }

    @PutMapping
    @Operation(operationId = "adminQuoteBrandsSave", summary = "Remplace la liste des marques (ordre conserve, doublons retires ; liste vide = saisie libre dans l'application)")
    public ResponseEntity<QuoteDtos.BrandList> enregistrer(@Valid @RequestBody QuoteDtos.BrandUpdate update) {
        return ResponseEntity.ok(quoteService.enregistrerMarques(update));
    }
}
