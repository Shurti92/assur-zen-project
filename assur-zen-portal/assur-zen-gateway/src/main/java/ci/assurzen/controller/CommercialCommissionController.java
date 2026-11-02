package ci.assurzen.controller;

import ci.assurzen.dto.CommercialCommissionPaymentRequest;
import ci.assurzen.dto.CommercialCommissionPaymentResponse;
import ci.assurzen.dto.CommercialCommissionSummaryResponse;
import ci.assurzen.dto.ContractCommercialCommissionResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.services.CommercialCommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Suivi et paiement de la commission par contrat des commerciaux internes (contrat "Commission
 * commerciale interne", 2026-09-08) - mirroir volontairement allege de CommissionController (pas de
 * demande de paiement partiel/avance, non demandee pour ce lot). Reserve aux administrateurs. */
@RestController
@RequestMapping("${env.ws}commercial-commissions")
@Tag(name = "Commissions commerciales", description = "Suivi et paiement de la commission par contrat des commerciaux internes")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CommercialCommissionController {

    private final CommercialCommissionService service;

    @GetMapping("/commercials")
    @Operation(operationId = "listCommercialCommissionSummaries",
            summary = "Lister les commerciaux avec le total de commissions payees et en attente")
    public ResponseEntity<List<CommercialCommissionSummaryResponse>> listCommercials() {
        return ResponseEntity.ok(service.listCommercialSummaries());
    }

    @GetMapping("/commercials/{commercialId}/contracts")
    @Operation(operationId = "listCommercialCommissionContracts",
            summary = "Lister les contrats (et leur statut de commission commerciale) d'un commercial")
    public ResponseEntity<List<ContractCommercialCommissionResponse>> listCommercialContracts(
            @PathVariable Long commercialId,
            @RequestParam(required = false) CommissionStatus status) {
        return ResponseEntity.ok(service.listCommercialContracts(commercialId, status));
    }

    @PostMapping("/pay")
    @Operation(operationId = "payCommercialCommissions",
            summary = "Payer la commission commerciale d'un ou plusieurs contrats via virement Wave (SycaPay) - "
                    + "chaque contrat est traite independamment, voir sycapayStatus sur chaque entree de la reponse")
    public ResponseEntity<List<CommercialCommissionPaymentResponse>> pay(@Valid @RequestBody CommercialCommissionPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.pay(request));
    }

    @GetMapping("/payments")
    @Operation(operationId = "listCommercialCommissionPayments",
            summary = "Historique des paiements de commissions commerciales (pagine, tri par date decroissante)")
    public ResponseEntity<PageResponse<CommercialCommissionPaymentResponse>> listPayments(
            @RequestParam(required = false) Long commercialId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.getPaymentHistory(commercialId, page, size));
    }
}
