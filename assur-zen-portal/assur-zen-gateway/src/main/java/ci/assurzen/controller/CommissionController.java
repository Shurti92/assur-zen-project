package ci.assurzen.controller;

import ci.assurzen.dto.CommissionPaymentRequest;
import ci.assurzen.dto.CommissionPaymentResponse;
import ci.assurzen.dto.ContractCommissionResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.PartialPaymentRequestResponse;
import ci.assurzen.dto.PartnerCommissionSummaryResponse;
import ci.assurzen.dto.PaymentRequestValidationItemResponse;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.PartialPaymentRequestStatus;
import ci.assurzen.services.CommissionPartialPaymentService;
import ci.assurzen.services.CommissionService;
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

@RestController
@RequestMapping("${env.ws}commissions")
@Tag(name = "Commissions", description = "Suivi et paiement des commissions des partenaires")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CommissionController {

    private final CommissionService commissionService;
    private final CommissionPartialPaymentService commissionPartialPaymentService;

    @GetMapping("/partners")
    @Operation(operationId = "listPartnerCommissionSummaries",
            summary = "Lister les partenaires avec le total de commissions payees et en attente")
    public ResponseEntity<List<PartnerCommissionSummaryResponse>> listPartners() {
        return ResponseEntity.ok(commissionService.listPartnerSummaries());
    }

    @GetMapping("/partners/{partenaireId}/contracts")
    @Operation(operationId = "listPartnerCommissionContracts",
            summary = "Lister les contrats (et leur statut de commission) d'un partenaire")
    public ResponseEntity<List<ContractCommissionResponse>> listPartnerContracts(
            @PathVariable Long partenaireId,
            @RequestParam(required = false) CommissionStatus status) {
        return ResponseEntity.ok(commissionService.listPartnerContracts(partenaireId, status));
    }

    @PostMapping("/pay")
    @Operation(operationId = "payCommissions",
            summary = "Payer la commission d'un ou plusieurs contrats (tout ou rien)")
    public ResponseEntity<List<CommissionPaymentResponse>> pay(@Valid @RequestBody CommissionPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commissionService.pay(request));
    }

    @GetMapping("/partial-payment-requests")
    @Operation(operationId = "listPaymentRequestsForValidation",
            summary = "Lister les demandes de paiement de commission a valider (partenaires et commerciaux), plus recentes d'abord - status optionnel (pending | paid)")
    public ResponseEntity<List<PaymentRequestValidationItemResponse>> listPaymentRequests(
            @RequestParam(required = false) PartialPaymentRequestStatus status) {
        return ResponseEntity.ok(commissionPartialPaymentService.listerPourValidation(status));
    }

    @PostMapping("/partial-payment-requests/{id}/valider")
    @Operation(operationId = "validatePartialCommissionPaymentRequest",
            summary = "Valider (admin/finance) une demande de paiement en attente, de partenaire ou de commercial - marque la demande payee (versement effectue hors systeme)")
    public ResponseEntity<PartialPaymentRequestResponse> validatePartialPaymentRequest(@PathVariable Long id) {
        return ResponseEntity.ok(commissionPartialPaymentService.valider(id));
    }

    @GetMapping("/payments")
    @Operation(operationId = "listCommissionPayments",
            summary = "Historique des paiements de commissions (pagine, tri par date decroissante)")
    public ResponseEntity<PageResponse<CommissionPaymentResponse>> listPayments(
            @RequestParam(required = false) Long partenaireId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(commissionService.getPaymentHistory(partenaireId, page, size));
    }
}
