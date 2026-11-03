package ci.assurzen.controller;

import ci.assurzen.dto.QuoteDtos;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.MobileQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Devis en ligne du client mobile, lot 2 (2026-11-03). */
@RestController
@RequestMapping("${env.ws}mobile/quotes")
@Tag(name = "Mobile - Devis", description = "Devis en ligne enregistres du client connecte")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class MobileQuoteController {

    private final MobileQuoteService quoteService;

    @PostMapping("/moto")
    @Operation(operationId = "mobileQuoteCreateMoto", summary = "Enregistre un devis moto (prix fige) ; memes champs et controles que la simulation")
    public ResponseEntity<QuoteDtos.QuoteView> creer(Authentication a, @Valid @RequestBody QuoteDtos.MotoSimulationRequest request) {
        return ResponseEntity.status(201).body(quoteService.creerMoto(userId(a), request));
    }

    @GetMapping
    @Operation(operationId = "mobileQuoteList", summary = "Mes devis, du plus recent au plus ancien")
    public ResponseEntity<QuoteDtos.QuoteList> lister(Authentication a) {
        return ResponseEntity.ok(quoteService.lister(userId(a)));
    }

    @GetMapping("/{reference}")
    @Operation(operationId = "mobileQuoteDetail", summary = "Detail d'un de mes devis")
    public ResponseEntity<QuoteDtos.QuoteView> detail(Authentication a, @PathVariable String reference) {
        return ResponseEntity.ok(quoteService.detail(userId(a), reference));
    }

    @PostMapping("/{reference}/accept")
    @Operation(operationId = "mobileQuoteAccept", summary = "Accepte un devis encore valide (409 QUOTE_EXPIRED sinon)")
    public ResponseEntity<QuoteDtos.QuoteView> accepter(Authentication a, @PathVariable String reference) {
        return ResponseEntity.ok(quoteService.accepter(userId(a), reference));
    }

    private Long userId(Authentication a) {
        return ((AuthenticatedUserPrincipal) a.getPrincipal()).userId();
    }
}
