package ci.assurzen.controller;

import ci.assurzen.dto.CommercialCommissionSelfResponse;
import ci.assurzen.dto.CommercialPaymentRequestCreateRequest;
import ci.assurzen.dto.CommercialPaymentRequestResponse;
import ci.assurzen.dto.CommercialPaymentRequestsResponse;
import ci.assurzen.dto.CommercialRequest;
import ci.assurzen.dto.CommercialResponse;
import ci.assurzen.dto.CommercialStatusRequest;
import ci.assurzen.dto.CommercialUpdateRequest;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.StatutCommercial;
import ci.assurzen.services.CommercialCommissionService;
import ci.assurzen.services.CommercialPaymentRequestService;
import ci.assurzen.services.CommercialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fiches commerciales. Le portefeuille de contrats d'un commercial reste consulte via
 * GET {@code ${env.ws}contracts} (deja scope automatiquement a Contract.salesRep pour tout compte non
 * ADMIN, cf. ContractsService.filter) - pas de doublon /commercials/me/contracts.
 */
@RestController
@RequestMapping("${env.ws}commercials")
@Tag(name = "Commerciaux", description = "Gestion des commerciaux, de leur compte et de leur remuneration")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CommercialController {

    private final CommercialService service;
    private final CommercialCommissionService commercialCommissionService;
    private final CommercialPaymentRequestService commercialPaymentRequestService;

    @GetMapping
    @Operation(operationId = "listCommercials", summary = "Lister/filtrer les commerciaux (reserve aux administrateurs, contient la remuneration)")
    public ResponseEntity<PageResponse<CommercialResponse>> list(
            @RequestParam(required = false) StatutCommercial statut,
            @RequestParam(required = false) Long villePartenaireId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.list(statut, villePartenaireId, page, size));
    }

    @GetMapping("/options")
    @Operation(operationId = "listCommercialOptions", summary = "Force de vente interne (commerciaux actifs : nom, prenoms, matricule, ville) pour la liste 'Commercial' de la souscription - sans remuneration, ouvert a tout utilisateur authentifie")
    public ResponseEntity<java.util.List<ci.assurzen.dto.CommercialOptionResponse>> options() {
        return ResponseEntity.ok(service.options());
    }

    @PostMapping
    @Operation(operationId = "createCommercial", summary = "Creer un commercial (cree aussi son compte de connexion, roles=COMMERCIAL)")
    public ResponseEntity<CommercialResponse> create(@Valid @RequestBody CommercialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/me")
    @Operation(operationId = "getMyCommercialProfile", summary = "Consulter sa propre fiche commerciale")
    public ResponseEntity<CommercialResponse> me() {
        return ResponseEntity.ok(service.me());
    }

    @GetMapping("/me/commissions")
    @Operation(operationId = "getMyCommercialCommissions", summary = "Consulter le resume et le detail de sa propre commission commerciale (en attente/payee)")
    public ResponseEntity<CommercialCommissionSelfResponse> mesCommissions() {
        return ResponseEntity.ok(commercialCommissionService.mySummary());
    }

    @GetMapping("/me/commissions/payment-requests")
    @Operation(operationId = "getMyCommissionPaymentRequests",
            summary = "Solde de commission disponible et historique de ses demandes de paiement (commercial connecte)")
    public ResponseEntity<CommercialPaymentRequestsResponse> mesDemandesDePaiement() {
        return ResponseEntity.ok(commercialPaymentRequestService.mesDemandes());
    }

    @PostMapping("/me/commissions/payment-requests")
    @Operation(operationId = "requestMyCommissionPayment",
            summary = "Demander le paiement total ou partiel de sa commission (montant revalide cote serveur). "
                    + "Header optionnel 'Idempotency-Key' : un rejeu de la meme cle renvoie la demande deja creee.")
    public ResponseEntity<CommercialPaymentRequestResponse> demanderPaiement(
            @Valid @RequestBody CommercialPaymentRequestCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commercialPaymentRequestService.demander(request.type(), request.amount(), idempotencyKey));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getCommercial", summary = "Consulter un commercial (soi-meme ou administrateur uniquement - contient la remuneration)")
    public ResponseEntity<CommercialResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateCommercial", summary = "Modifier un commercial (reserve aux administrateurs)")
    public ResponseEntity<CommercialResponse> update(@PathVariable Long id, @Valid @RequestBody CommercialUpdateRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/statut")
    @Operation(operationId = "changerStatutCommercial", summary = "Activer/suspendre/mettre fin a un commercial (reserve aux administrateurs)")
    public ResponseEntity<CommercialResponse> changerStatut(@PathVariable Long id, @Valid @RequestBody CommercialStatusRequest request) {
        return ResponseEntity.ok(service.changerStatut(id, request));
    }
}
