package ci.assurzen.controller;

import ci.assurzen.dto.QuoteDtos;
import ci.assurzen.service.BackOfficeQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Devis moto crees au back-office par les commerciaux et administrateurs (lot 3, 2026-11-03). */
@RestController
@RequestMapping("${env.ws}admin/quotes")
@Tag(name = "Back-office - Devis", description = "Devis moto crees pour un prospect (commercial : ses devis ; administrateur : tous)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class BackOfficeQuoteController {

    private final BackOfficeQuoteService quoteService;
    private final ci.assurzen.service.QuoteEmailService emailService;

    @PostMapping("/moto")
    @Operation(operationId = "backofficeQuoteCreateMoto", summary = "Cree un devis moto pour un prospect (prix fige, sans remise)")
    public ResponseEntity<QuoteDtos.QuoteView> creer(@Valid @RequestBody QuoteDtos.BackOfficeQuoteRequest request) {
        return ResponseEntity.status(201).body(emailService.envoyerSiDemande(quoteService.creerMoto(request), request.sendEmail()));
    }

    @GetMapping
    @Operation(operationId = "backofficeQuoteList", summary = "Liste des devis (filtres facultatifs : statut CALCULE|ACCEPTE|EXPIRE, q = nom, telephone, reference, immatriculation)")
    public ResponseEntity<QuoteDtos.QuoteList> lister(@RequestParam(required = false) String statut, @RequestParam(required = false) String q,
                                                      @RequestParam(required = false) String email) {
        return ResponseEntity.ok(quoteService.lister(statut, q, email));
    }

    @PostMapping("/{reference}/send-email")
    @Operation(operationId = "backofficeQuoteSendEmail", summary = "Envoie le devis par e-mail au prospect (PDF joint par defaut ; 409 QUOTE_EXPIRED, 429 au-dela de 5 envois par jour)")
    public ResponseEntity<QuoteDtos.QuoteView> envoyerEmail(@PathVariable String reference, @Valid @RequestBody(required = false) QuoteDtos.SendEmailRequest request) {
        return ResponseEntity.ok(emailService.envoyer(reference, request));
    }

    @GetMapping("/{reference}/emails")
    @Operation(operationId = "backofficeQuoteEmails", summary = "Historique des envois par e-mail d'un devis")
    public ResponseEntity<QuoteDtos.EmailHistory> historique(@PathVariable String reference) {
        return ResponseEntity.ok(emailService.historique(reference));
    }

    @GetMapping("/{reference}")
    @Operation(operationId = "backofficeQuoteDetail", summary = "Detail d'un devis")
    public ResponseEntity<QuoteDtos.QuoteView> detail(@PathVariable String reference) {
        return ResponseEntity.ok(quoteService.detail(reference));
    }

    @PostMapping("/{reference}/accept")
    @Operation(operationId = "backofficeQuoteAccept", summary = "Enregistre l'accord du prospect (409 QUOTE_EXPIRED si le devis est perime)")
    public ResponseEntity<QuoteDtos.QuoteView> accepter(@PathVariable String reference) {
        return ResponseEntity.ok(quoteService.accepter(reference));
    }
}
