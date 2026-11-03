package ci.assurzen.controller;

import ci.assurzen.dto.MobilePaymentDtos;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.MobilePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paiement depuis l'application mobile (demande n°4, 2026-11-02). SIMULATION en dev uniquement : voir MobilePaymentService.
 * Reserve au role CLIENT (SecurityConfig, /mobile/**).
 */
@RestController
@RequestMapping("${env.ws}mobile/payments")
@Tag(name = "Mobile - Paiements", description = "Paiement du renouvellement d'un contrat (simulation en dev, aucun fournisseur d'encaissement branche)")
@RequiredArgsConstructor
public class MobilePaymentController {

    private final MobilePaymentService service;

    @GetMapping("/methods")
    @Operation(operationId = "mobilePaymentMethods", summary = "Moyens de paiement et leur etat (enabled=false tant qu'aucun fournisseur n'est branche ; simulated=true en dev)")
    public ResponseEntity<MobilePaymentDtos.MethodList> moyens() {
        return ResponseEntity.ok(service.moyens());
    }

    @PostMapping
    @Operation(operationId = "mobileInitiatePayment", summary = "Initier le paiement d'un renouvellement (le montant vient du serveur ; 409 AMOUNT_MISMATCH si l'app envoie un autre montant) ; idempotent via idempotencyKey ou en-tete Idempotency-Key")
    public ResponseEntity<MobilePaymentDtos.Payment> initier(Authentication authentication,
                                                            @Valid @RequestBody MobilePaymentDtos.InitiateRequest request,
                                                            @RequestHeader(value = "Idempotency-Key", required = false) String cle) {
        return ResponseEntity.status(201).body(service.initier(utilisateur(authentication).userId(), request, cle));
    }

    @GetMapping("/{reference}")
    @Operation(operationId = "mobilePaymentStatus", summary = "Statut d'une tentative : PENDING, CONFIRMED (pose par le serveur uniquement) ou FAILED")
    public ResponseEntity<MobilePaymentDtos.Payment> statut(Authentication authentication, @PathVariable String reference) {
        return ResponseEntity.ok(service.statut(utilisateur(authentication).userId(), reference));
    }

    private AuthenticatedUserPrincipal utilisateur(Authentication authentication) {
        return (AuthenticatedUserPrincipal) authentication.getPrincipal();
    }
}
