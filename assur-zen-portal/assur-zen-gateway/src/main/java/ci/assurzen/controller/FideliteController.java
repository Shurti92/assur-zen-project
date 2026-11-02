package ci.assurzen.controller;

import ci.assurzen.dto.CompteFideliteAvantageResponse;
import ci.assurzen.dto.CompteFideliteResponse;
import ci.assurzen.dto.FidelisationAjustementRequest;
import ci.assurzen.dto.FidelisationHistoriqueResponse;
import ci.assurzen.dto.GestionnaireDedieRequest;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.RecompenseEchangeRequest;
import ci.assurzen.dto.RecompenseEchangeResponse;
import ci.assurzen.dto.StatutVipRequest;
import ci.assurzen.entities.EchangeStatut;
import ci.assurzen.entities.StatutVip;
import ci.assurzen.services.FideliteService;
import ci.assurzen.services.ScoreFideliteService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}fidelite")
@Tag(name = "Fidelite", description = "Comptes fidelite, historique de points et echanges de recompenses")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class FideliteController {

    private final FideliteService fideliteService;
    private final ScoreFideliteService scoreFideliteService;

    @GetMapping("/comptes")
    @Operation(operationId = "searchComptesFidelite", summary = "Lister/rechercher les comptes fidelite (paginé, filtrable par statut VIP et palier)")
    public ResponseEntity<PageResponse<CompteFideliteResponse>> search(
            @RequestParam(required = false) StatutVip statutVip,
            @RequestParam(required = false) Long palierId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(fideliteService.search(statutVip, palierId, page, size));
    }

    @GetMapping("/comptes/{insuredId}")
    @Operation(operationId = "getCompteFidelite", summary = "Consulter le compte fidelite d'un assure")
    public ResponseEntity<CompteFideliteResponse> getCompte(@PathVariable Long insuredId) {
        return ResponseEntity.ok(fideliteService.getCompte(insuredId));
    }

    @PostMapping("/comptes/{insuredId}/adherer")
    @Operation(operationId = "adhererFidelite", summary = "Inscrire un assure au programme de fidelite (cree son compte s'il n'existe pas)")
    public ResponseEntity<CompteFideliteResponse> adherer(@PathVariable Long insuredId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fideliteService.adherer(insuredId));
    }

    @GetMapping("/comptes/{insuredId}/historique")
    @Operation(operationId = "getHistoriqueFidelite", summary = "Historique des mouvements de points d'un compte fidelite")
    public ResponseEntity<List<FidelisationHistoriqueResponse>> historique(@PathVariable Long insuredId) {
        return ResponseEntity.ok(fideliteService.listHistorique(insuredId));
    }

    @PostMapping("/ajustements")
    @Operation(operationId = "ajusterFidelite", summary = "Ajustement manuel du solde de points (reserve aux administrateurs/managers)")
    public ResponseEntity<FidelisationHistoriqueResponse> ajuster(@Valid @RequestBody FidelisationAjustementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fideliteService.ajusterManuellement(request));
    }

    @PostMapping("/echanges")
    @Operation(operationId = "echangerRecompense", summary = "Echanger des points contre une recompense du catalogue")
    public ResponseEntity<RecompenseEchangeResponse> echanger(@Valid @RequestBody RecompenseEchangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fideliteService.echanger(request));
    }

    @PatchMapping("/echanges/{id}")
    @Operation(operationId = "traiterEchangeRecompense", summary = "Valider/livrer/annuler un echange de recompense (reserve aux administrateurs/managers)")
    public ResponseEntity<RecompenseEchangeResponse> traiterEchange(@PathVariable Long id, @RequestParam EchangeStatut statut) {
        return ResponseEntity.ok(fideliteService.traiterEchange(id, statut));
    }

    @PostMapping("/comptes/{insuredId}/recalculer-score")
    @Operation(operationId = "recalculerScoreFidelite", summary = "Recalculer le score de fidelite / statut VIP d'un assure a partir des regles actives (reserve aux administrateurs/managers)")
    public ResponseEntity<CompteFideliteResponse> recalculerScore(@PathVariable Long insuredId) {
        return ResponseEntity.ok(scoreFideliteService.recalculer(insuredId));
    }

    @PatchMapping("/comptes/{insuredId}/statut-vip")
    @Operation(operationId = "changerStatutVip", summary = "Forcer manuellement le statut VIP d'un compte (reserve aux administrateurs/managers)")
    public ResponseEntity<CompteFideliteResponse> changerStatutVip(@PathVariable Long insuredId, @Valid @RequestBody StatutVipRequest request) {
        return ResponseEntity.ok(fideliteService.changerStatutVip(insuredId, request.statut(), request.motif()));
    }

    @PatchMapping("/comptes/{insuredId}/gestionnaire")
    @Operation(operationId = "assignerGestionnaireVip", summary = "Assigner (ou retirer) le gestionnaire dedie d'un compte VIP (reserve aux administrateurs/managers)")
    public ResponseEntity<CompteFideliteResponse> assignerGestionnaire(@PathVariable Long insuredId, @Valid @RequestBody GestionnaireDedieRequest request) {
        return ResponseEntity.ok(fideliteService.assignerGestionnaire(insuredId, request.gestionnaireId()));
    }

    @GetMapping("/comptes/{insuredId}/avantages")
    @Operation(operationId = "listAvantagesFidelite", summary = "Lister les avantages accordes a un compte fidelite")
    public ResponseEntity<List<CompteFideliteAvantageResponse>> avantages(@PathVariable Long insuredId) {
        return ResponseEntity.ok(fideliteService.listAvantages(insuredId));
    }
}
