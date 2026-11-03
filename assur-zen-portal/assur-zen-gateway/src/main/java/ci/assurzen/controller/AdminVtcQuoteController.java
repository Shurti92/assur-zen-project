package ci.assurzen.controller;

import ci.assurzen.dto.QuoteDtos;
import ci.assurzen.service.BackOfficeQuoteService;
import ci.assurzen.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Devis VTC au back-office (creation : ADMIN et COMMERCIAL) et parametrage VTC (ADMIN) (2026-11-03). */
@RestController
@RequestMapping("${env.ws}admin/quotes/vtc")
@Tag(name = "Back-office - Devis VTC", description = "Devis VTC pour un prospect et parametrage (marques, plateformes, plafond de remise)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AdminVtcQuoteController {

    private final QuoteService quoteService;
    private final BackOfficeQuoteService backOfficeQuoteService;

    @PostMapping
    @Operation(operationId = "backofficeQuoteCreateVtc", summary = "Cree un devis VTC pour un prospect ; discountPercent facultatif, plafonne par le parametre VTC")
    public ResponseEntity<QuoteDtos.QuoteView> creer(@Valid @RequestBody QuoteDtos.BackOfficeVtcQuoteRequest request) {
        return ResponseEntity.status(201).body(backOfficeQuoteService.creerVtc(request));
    }

    @GetMapping("/brands")
    @Operation(operationId = "adminQuoteVtcBrands", summary = "Marques de vehicules proposees pour le VTC")
    public ResponseEntity<QuoteDtos.BrandList> marques() {
        return ResponseEntity.ok(quoteService.listerMarques("vtc"));
    }

    @PutMapping("/brands")
    @Operation(operationId = "adminQuoteVtcBrandsSave", summary = "Remplace la liste des marques VTC (ordre conserve, doublons retires)")
    public ResponseEntity<QuoteDtos.BrandList> enregistrerMarques(@Valid @RequestBody QuoteDtos.BrandUpdate update) {
        return ResponseEntity.ok(quoteService.enregistrerMarques("vtc", update));
    }

    @GetMapping("/platforms")
    @Operation(operationId = "adminQuoteVtcPlatforms", summary = "Plateformes VTC proposees (Yango, Heetch...)")
    public ResponseEntity<QuoteDtos.PlatformList> plateformes() {
        return ResponseEntity.ok(quoteService.listerPlateformes());
    }

    @PutMapping("/platforms")
    @Operation(operationId = "adminQuoteVtcPlatformsSave", summary = "Remplace la liste des plateformes VTC")
    public ResponseEntity<QuoteDtos.PlatformList> enregistrerPlateformes(@Valid @RequestBody QuoteDtos.PlatformUpdate update) {
        return ResponseEntity.ok(quoteService.enregistrerPlateformes(update));
    }

    @GetMapping("/discount-limit")
    @Operation(operationId = "backofficeQuoteVtcDiscountLimit", summary = "Plafond de remise VTC en % de la prime nette (lecture : ADMIN et COMMERCIAL)")
    public ResponseEntity<QuoteDtos.VtcSettings> plafondRemise() {
        return ResponseEntity.ok(quoteService.limiteRemiseVtc());
    }

    @GetMapping("/settings")
    @Operation(operationId = "adminQuoteVtcSettings", summary = "Parametres VTC : plafond de remise (en % de la prime nette)")
    public ResponseEntity<QuoteDtos.VtcSettings> parametres() {
        return ResponseEntity.ok(quoteService.lireParametresVtc());
    }

    @PutMapping("/settings")
    @Operation(operationId = "adminQuoteVtcSettingsSave", summary = "Modifie le plafond de remise VTC (0 a 100 %)")
    public ResponseEntity<QuoteDtos.VtcSettings> enregistrerParametres(@Valid @RequestBody QuoteDtos.VtcSettings settings) {
        return ResponseEntity.ok(quoteService.enregistrerParametresVtc(settings));
    }
}
