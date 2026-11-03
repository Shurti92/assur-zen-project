package ci.assurzen.controller;

import ci.assurzen.dto.AdminMobileDtos;
import ci.assurzen.service.AdminMobileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administration de l'application mobile pour le back-office (2026-11-03) : comptes clients, demandes de suppression de
 * compte, demandes de modification / resiliation de contrat et demandes de rappel. Reserve au role ADMIN
 * (SecurityConfig, /admin/mobile/**, et verification dans le service).
 */
@RestController
@RequestMapping("${env.ws}admin/mobile")
@Tag(name = "Administration mobile", description = "Comptes clients et demandes de l'application mobile (reserve aux administrateurs)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AdminMobileController {

    private final AdminMobileService service;

    // ----- comptes

    @GetMapping("/accounts")
    @Operation(operationId = "adminMobileListAccounts", summary = "Comptes mobile (role CLIENT), filtrables par numero ou nom (q) et par etat (active), pagines")
    public ResponseEntity<AdminMobileDtos.AccountPage> comptes(@RequestParam(required = false) String q,
                                                              @RequestParam(required = false) Boolean active,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.comptes(q, active, page, size));
    }

    @GetMapping("/accounts/{userId}")
    @Operation(operationId = "adminMobileGetAccount", summary = "Detail d'un compte mobile avec les assures rattaches")
    public ResponseEntity<AdminMobileDtos.AccountDetail> compte(@PathVariable Long userId) {
        return ResponseEntity.ok(service.compte(userId));
    }

    @PostMapping("/accounts")
    @Operation(operationId = "adminMobileCreateAccount", summary = "Creer un compte mobile pour un assure existant (identifiant = telephone). Aucun PIN n'est defini : le client le choisit via \"PIN oublie\" (SMS d'invitation envoye sauf sendInvitation=false). 409 PHONE_ALREADY_REGISTERED si le numero a deja un compte")
    public ResponseEntity<AdminMobileDtos.AccountDetail> creer(@Valid @RequestBody AdminMobileDtos.CreateAccountRequest request) {
        return ResponseEntity.status(201).body(service.creerCompte(request));
    }

    @PatchMapping("/accounts/{userId}/active")
    @Operation(operationId = "adminMobileSetAccountActive", summary = "Activer ou desactiver un compte mobile (la desactivation coupe les sessions)")
    public ResponseEntity<AdminMobileDtos.AccountDetail> activer(@PathVariable Long userId, @Valid @RequestBody AdminMobileDtos.ActiveRequest request) {
        return ResponseEntity.ok(service.changerActivation(userId, request.active(), request.comment()));
    }

    @PostMapping("/accounts/{userId}/insureds")
    @Operation(operationId = "adminMobileLinkInsured", summary = "Rattacher un assure a un compte mobile (le client voit alors ses contrats et sinistres)")
    public ResponseEntity<AdminMobileDtos.AccountDetail> rattacher(@PathVariable Long userId, @Valid @RequestBody AdminMobileDtos.LinkRequest request) {
        return ResponseEntity.ok(service.rattacher(userId, request.insuredId()));
    }

    @DeleteMapping("/accounts/{userId}/insureds/{insuredId}")
    @Operation(operationId = "adminMobileUnlinkInsured", summary = "Detacher un assure d'un compte mobile (corrige un rattachement errone, par exemple un numero recycle)")
    public ResponseEntity<AdminMobileDtos.AccountDetail> detacher(@PathVariable Long userId, @PathVariable Long insuredId) {
        return ResponseEntity.ok(service.detacher(userId, insuredId));
    }

    // ----- suppressions de compte

    @GetMapping("/deletion-requests")
    @Operation(operationId = "adminMobileListDeletionRequests", summary = "Demandes de suppression de compte (statut : EN_ATTENTE, TRAITEE, REFUSEE), les 200 plus recentes")
    public ResponseEntity<List<AdminMobileDtos.DeletionRequestItem>> suppressions(@RequestParam(required = false) String statut) {
        return ResponseEntity.ok(service.suppressions(statut));
    }

    @PostMapping("/deletion-requests/{id}/process")
    @Operation(operationId = "adminMobileProcessDeletionRequest", summary = "Traiter une demande de suppression : APPROVE (irreversible, confirm=true : compte anonymise, rattachements retires, contrats conserves) ou REJECT (compte reactive)")
    public ResponseEntity<AdminMobileDtos.DeletionRequestItem> traiterSuppression(@PathVariable Long id,
                                                                                @Valid @RequestBody AdminMobileDtos.ProcessDeletionRequest request) {
        return ResponseEntity.ok(service.traiterSuppression(id, request));
    }

    // ----- demandes sur contrat

    @GetMapping("/contract-requests")
    @Operation(operationId = "adminMobileListContractRequests", summary = "Demandes de modification / resiliation de contrat faites depuis le mobile (statut : EN_ATTENTE, TRAITEE, REFUSEE)")
    public ResponseEntity<List<AdminMobileDtos.ContractRequestItem>> demandesContrat(@RequestParam(required = false) String statut) {
        return ResponseEntity.ok(service.demandesContrat(statut));
    }

    @PostMapping("/contract-requests/{id}/process")
    @Operation(operationId = "adminMobileProcessContractRequest", summary = "Marquer une demande de contrat TRAITEE ou REFUSEE (n'altere pas le contrat : l'avenant ou la resiliation se font dans le back-office)")
    public ResponseEntity<AdminMobileDtos.ContractRequestItem> traiterDemandeContrat(@PathVariable Long id,
                                                                                    @Valid @RequestBody AdminMobileDtos.ProcessRequest request) {
        return ResponseEntity.ok(service.traiterDemandeContrat(id, request));
    }

    // ----- rappels

    @GetMapping("/callbacks")
    @Operation(operationId = "adminMobileListCallbacks", summary = "Demandes de rappel (statut : EN_ATTENTE, RAPPELE, ANNULE)")
    public ResponseEntity<List<AdminMobileDtos.CallbackItem>> rappels(@RequestParam(required = false) String statut) {
        return ResponseEntity.ok(service.rappels(statut));
    }

    @PostMapping("/callbacks/{id}/process")
    @Operation(operationId = "adminMobileProcessCallback", summary = "Marquer une demande de rappel RAPPELE ou ANNULE")
    public ResponseEntity<AdminMobileDtos.CallbackItem> traiterRappel(@PathVariable Long id, @Valid @RequestBody AdminMobileDtos.ProcessRequest request) {
        return ResponseEntity.ok(service.traiterRappel(id, request));
    }
}
