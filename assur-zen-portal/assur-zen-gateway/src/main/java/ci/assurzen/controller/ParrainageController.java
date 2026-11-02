package ci.assurzen.controller;

import ci.assurzen.dto.ControleAntifraudeParrainageResponse;
import ci.assurzen.dto.HistoriqueParrainageResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.ParrainageContratRequest;
import ci.assurzen.dto.ParrainageInscriptionRequest;
import ci.assurzen.dto.ParrainageMotifRequest;
import ci.assurzen.dto.ParrainageRequest;
import ci.assurzen.dto.ParrainageResponse;
import ci.assurzen.dto.ParrainageValidationResponse;
import ci.assurzen.entities.StatutParrainage;
import ci.assurzen.services.ParrainageService;
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
@RequestMapping("${env.ws}parrainage/parrainages")
@Tag(name = "Parrainage - Parrainages", description = "Cycle de vie complet d'un parrainage : creation, invitation, inscription du filleul, rattachement du contrat, validation d'eligibilite, annulation")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ParrainageController {

    private final ParrainageService parrainageService;

    @GetMapping
    @Operation(operationId = "listParrainages", summary = "Lister/rechercher les parrainages (pagine, filtrable)")
    public ResponseEntity<PageResponse<ParrainageResponse>> search(
            @RequestParam(required = false) StatutParrainage statut,
            @RequestParam(required = false) Long programmeId,
            @RequestParam(required = false) Long parrainId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(parrainageService.search(statut, programmeId, parrainId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getParrainage", summary = "Detail d'un parrainage")
    public ResponseEntity<ParrainageResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(parrainageService.get(id));
    }

    @PostMapping
    @Operation(operationId = "creerParrainage", summary = "Creer un parrainage (via un code de parrainage, ou directement parrainInsuredId + programmeId)")
    public ResponseEntity<ParrainageResponse> creer(@Valid @RequestBody ParrainageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parrainageService.creer(request));
    }

    @PostMapping("/{id}/inviter")
    @Operation(operationId = "inviterParrainage", summary = "Envoyer (ou renvoyer) l'invitation SMS au filleul")
    public ResponseEntity<ParrainageResponse> inviter(@PathVariable Long id) {
        return ResponseEntity.ok(parrainageService.inviter(id));
    }

    @PostMapping("/{id}/inscription")
    @Operation(operationId = "inscrireFilleulParrainage", summary = "Rattacher le filleul une fois inscrit comme assure")
    public ResponseEntity<ParrainageResponse> inscrireFilleul(@PathVariable Long id, @Valid @RequestBody ParrainageInscriptionRequest request) {
        return ResponseEntity.ok(parrainageService.inscrireFilleul(id, request));
    }

    @PostMapping("/{id}/souscription")
    @Operation(operationId = "rattacherContratParrainage", summary = "Rattacher le contrat souscrit par le filleul")
    public ResponseEntity<ParrainageResponse> rattacherContrat(@PathVariable Long id, @Valid @RequestBody ParrainageContratRequest request) {
        return ResponseEntity.ok(parrainageService.rattacherContrat(id, request));
    }

    @PostMapping("/{id}/valider")
    @Operation(operationId = "validerParrainage", summary = "Reevaluer l'eligibilite complete (programme actif, parrain actif, filleul nouveau client, contrat actif, produit eligible, quota) et generer les recompenses si eligible")
    public ResponseEntity<ParrainageValidationResponse> valider(@PathVariable Long id) {
        return ResponseEntity.ok(parrainageService.valider(id));
    }

    @PostMapping("/{id}/annuler")
    @Operation(operationId = "annulerParrainage", summary = "Annuler un parrainage et reverser toute recompense deja attribuee")
    public ResponseEntity<ParrainageResponse> annuler(@PathVariable Long id, @Valid @RequestBody(required = false) ParrainageMotifRequest request) {
        return ResponseEntity.ok(parrainageService.annuler(id, request == null ? null : request.motif()));
    }

    @PostMapping("/{id}/expirer")
    @Operation(operationId = "expirerParrainage", summary = "Marquer un parrainage comme expire")
    public ResponseEntity<ParrainageResponse> expirer(@PathVariable Long id) {
        return ResponseEntity.ok(parrainageService.expirer(id));
    }

    @GetMapping("/{id}/historique")
    @Operation(operationId = "getParrainageHistorique", summary = "Historique complet d'un parrainage")
    public ResponseEntity<List<HistoriqueParrainageResponse>> historique(@PathVariable Long id) {
        return ResponseEntity.ok(parrainageService.historique(id));
    }

    @GetMapping("/{id}/controles-antifraude")
    @Operation(operationId = "getParrainageControlesAntifraude", summary = "Lister les controles anti-fraude executes sur ce parrainage")
    public ResponseEntity<List<ControleAntifraudeParrainageResponse>> controlesAntifraude(@PathVariable Long id) {
        return ResponseEntity.ok(parrainageService.controlesAntifraude(id));
    }

    @PostMapping("/{id}/controles-antifraude")
    @Operation(operationId = "declencherControleAntifraudeParrainage", summary = "Re-executer les controles anti-fraude a la demande")
    public ResponseEntity<List<ControleAntifraudeParrainageResponse>> declencherControleAntifraude(@PathVariable Long id) {
        return ResponseEntity.ok(parrainageService.declencherControleAntifraude(id));
    }
}
