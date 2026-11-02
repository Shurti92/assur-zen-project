package ci.assurzen.controller;

import ci.assurzen.dto.CancelFundOutflowRequest;
import ci.assurzen.dto.CreateFundOutflowRequest;
import ci.assurzen.dto.DisburseFundOutflowRequest;
import ci.assurzen.dto.FundOutflowDisbursementResponse;
import ci.assurzen.dto.FundOutflowResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.RejectFundOutflowRequest;
import ci.assurzen.dto.UpdateFundOutflowRequest;
import ci.assurzen.dto.HistoriqueModificationResponse;
import ci.assurzen.entities.FundOutflowPriority;
import ci.assurzen.entities.FundOutflowStatus;
import ci.assurzen.services.FundOutflowService;
import ci.assurzen.services.HistoriqueModificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("${env.ws}fund-outflows")
@Tag(name = "Sorties de fonds", description = "Moteur financier generique - demandes, workflow, decaissement (Phase 1)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class FundOutflowController {

    private final FundOutflowService service;
    private final HistoriqueModificationService historiqueModificationService;

    @PostMapping
    @Operation(operationId = "createFundOutflow", summary = "Creer une demande de sortie de fonds (reserve aux administrateurs)")
    public ResponseEntity<FundOutflowResponse> create(@Valid @RequestBody CreateFundOutflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getFundOutflow", summary = "Consulter une sortie de fonds")
    public ResponseEntity<FundOutflowResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @GetMapping("/search")
    @Operation(operationId = "searchFundOutflows", summary = "Rechercher des sorties de fonds (pagine, filtrable)")
    public ResponseEntity<PageResponse<FundOutflowResponse>> search(
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) FundOutflowStatus status,
            @RequestParam(required = false) Long outflowTypeId,
            @RequestParam(required = false) String beneficiaryType,
            @RequestParam(required = false) Long beneficiaryId,
            @RequestParam(required = false) Long amountMin,
            @RequestParam(required = false) Long amountMax,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) FundOutflowPriority priority,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.search(reference, status, outflowTypeId, beneficiaryType, beneficiaryId,
                amountMin, amountMax, dateFrom, dateTo, priority, page, size));
    }

    @GetMapping("/pending-approvals")
    @Operation(operationId = "listFundOutflowPendingApprovals", summary = "Lister les sorties en attente d'approbation (UNDER_REVIEW)")
    public ResponseEntity<PageResponse<FundOutflowResponse>> pendingApprovals(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.listPendingApprovals(page, size));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateFundOutflow", summary = "Modifier une sortie de fonds (uniquement a l'etat DRAFT, reserve aux administrateurs)")
    public ResponseEntity<FundOutflowResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateFundOutflowRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteFundOutflow", summary = "Supprimer (logiquement) une sortie de fonds non engagee (reserve aux administrateurs)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    @Operation(operationId = "submitFundOutflow", summary = "Soumettre une sortie de fonds (DRAFT -> SUBMITTED, reserve aux administrateurs)")
    public ResponseEntity<FundOutflowResponse> submit(@PathVariable Long id) {
        return ResponseEntity.ok(service.submit(id));
    }

    @PostMapping("/{id}/review")
    @Operation(operationId = "reviewFundOutflow", summary = "Passer en examen (SUBMITTED -> UNDER_REVIEW, reserve aux administrateurs)")
    public ResponseEntity<FundOutflowResponse> review(@PathVariable Long id) {
        return ResponseEntity.ok(service.review(id));
    }

    @PostMapping("/{id}/approve")
    @Operation(operationId = "approveFundOutflow", summary = "Approuver (UNDER_REVIEW -> APPROVED, reserve aux administrateurs)")
    public ResponseEntity<FundOutflowResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    @Operation(operationId = "rejectFundOutflow", summary = "Rejeter avec motif obligatoire (UNDER_REVIEW -> REJECTED, reserve aux administrateurs)")
    public ResponseEntity<FundOutflowResponse> reject(@PathVariable Long id, @Valid @RequestBody RejectFundOutflowRequest request) {
        return ResponseEntity.ok(service.reject(id, request.reason()));
    }

    @PostMapping("/{id}/authorize")
    @Operation(operationId = "authorizeFundOutflow", summary = "Autoriser le decaissement (APPROVED -> AUTHORIZED, reserve aux administrateurs)")
    public ResponseEntity<FundOutflowResponse> authorize(@PathVariable Long id) {
        return ResponseEntity.ok(service.authorize(id));
    }

    @PostMapping("/{id}/disburse")
    @Operation(operationId = "disburseFundOutflow", summary = "Decaisser (AUTHORIZED -> DISBURSED, reserve aux administrateurs). "
            + "Header optionnel 'Idempotency-Key' (UUID v4, recommande) : un rejeu de la meme cle pour cette sortie renvoie le decaissement deja cree au lieu d'en creer un second.")
    public ResponseEntity<FundOutflowDisbursementResponse> disburse(@PathVariable Long id,
            @Valid @RequestBody DisburseFundOutflowRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.disburse(id, request, idempotencyKey));
    }

    @PostMapping("/{id}/cancel")
    @Operation(operationId = "cancelFundOutflow", summary = "Annuler avec motif obligatoire (DRAFT/SUBMITTED/UNDER_REVIEW -> CANCELLED, reserve aux administrateurs)")
    public ResponseEntity<FundOutflowResponse> cancel(@PathVariable Long id, @Valid @RequestBody CancelFundOutflowRequest request) {
        return ResponseEntity.ok(service.cancel(id, request.reason()));
    }

    @PostMapping("/{id}/close")
    @Operation(operationId = "closeFundOutflow", summary = "Cloturer (DISBURSED/REPAID/CANCELLED/REJECTED -> CLOSED, reserve aux administrateurs)")
    public ResponseEntity<FundOutflowResponse> close(@PathVariable Long id) {
        return ResponseEntity.ok(service.close(id));
    }

    @GetMapping("/{id}/history")
    @Operation(operationId = "getFundOutflowHistory", summary = "Historique des transitions de statut de cette sortie de fonds")
    public ResponseEntity<PageResponse<HistoriqueModificationResponse>> history(@PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        String reference = service.get(id).reference();
        return ResponseEntity.ok(historiqueModificationService.search("SORTIES_FONDS", "FundOutflow", reference, null, null, null, null, null, page, size));
    }

    @GetMapping("/{id}/audit")
    @Operation(operationId = "getFundOutflowAudit", summary = "Trace d'audit complete de cette sortie de fonds (meme source que /history)")
    public ResponseEntity<PageResponse<HistoriqueModificationResponse>> audit(@PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        String reference = service.get(id).reference();
        return ResponseEntity.ok(historiqueModificationService.search("SORTIES_FONDS", "FundOutflow", reference, null, null, null, null, null, page, size));
    }
}
