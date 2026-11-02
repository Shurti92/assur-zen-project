package ci.assurzen.controller;

import ci.assurzen.dto.CrossSellAffectationRequest;
import ci.assurzen.dto.CrossSellHistoryResponse;
import ci.assurzen.dto.CrossSellOpportunityRequest;
import ci.assurzen.dto.CrossSellOpportunityResponse;
import ci.assurzen.dto.CrossSellStatutRequest;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.CrossSellStatut;
import ci.assurzen.entities.ReclamationPriorite;
import ci.assurzen.services.CrossSellOpportunityService;
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
@RequestMapping("${env.ws}cross-sell/opportunites")
@Tag(name = "Vente Croisee - Opportunites", description = "Cycle de vie des opportunites de vente croisee detectees ou creees manuellement")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CrossSellOpportunityController {

    private final CrossSellOpportunityService crossSellOpportunityService;

    @GetMapping
    @Operation(operationId = "listCrossSellOpportunities", summary = "Lister/rechercher les opportunites (paginé, filtrable)")
    public ResponseEntity<PageResponse<CrossSellOpportunityResponse>> search(
            @RequestParam(required = false) CrossSellStatut statut,
            @RequestParam(required = false) ReclamationPriorite priorite,
            @RequestParam(required = false) Long commercialId,
            @RequestParam(required = false) Long insuredId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(crossSellOpportunityService.search(statut, priorite, commercialId, insuredId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getCrossSellOpportunity", summary = "Detail d'une opportunite")
    public ResponseEntity<CrossSellOpportunityResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(crossSellOpportunityService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createCrossSellOpportunity", summary = "Creer une opportunite manuellement")
    public ResponseEntity<CrossSellOpportunityResponse> create(@Valid @RequestBody CrossSellOpportunityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crossSellOpportunityService.create(request));
    }

    @PostMapping("/generer")
    @Operation(operationId = "genererCrossSellOpportunities", summary = "Lancer le moteur de detection (un assure donne, ou tous si insuredId absent) (reserve aux administrateurs/managers)")
    public ResponseEntity<List<CrossSellOpportunityResponse>> generer(@RequestParam(required = false) Long insuredId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crossSellOpportunityService.generer(insuredId));
    }

    @PatchMapping("/{id}/affectation")
    @Operation(operationId = "affecterCrossSellOpportunity", summary = "Affecter l'opportunite a un commercial (reserve aux administrateurs/managers)")
    public ResponseEntity<CrossSellOpportunityResponse> affecter(@PathVariable Long id, @Valid @RequestBody CrossSellAffectationRequest request) {
        return ResponseEntity.ok(crossSellOpportunityService.affecter(id, request));
    }

    @PatchMapping("/{id}/statut")
    @Operation(operationId = "changerStatutCrossSellOpportunity", summary = "Faire avancer le statut de l'opportunite (qualification, acceptation, refus, conversion...) (reserve aux administrateurs/managers)")
    public ResponseEntity<CrossSellOpportunityResponse> changerStatut(@PathVariable Long id, @Valid @RequestBody CrossSellStatutRequest request) {
        return ResponseEntity.ok(crossSellOpportunityService.changerStatut(id, request));
    }

    @GetMapping("/{id}/historique")
    @Operation(operationId = "getCrossSellOpportunityHistorique", summary = "Historique complet d'une opportunite")
    public ResponseEntity<List<CrossSellHistoryResponse>> historique(@PathVariable Long id) {
        return ResponseEntity.ok(crossSellOpportunityService.historique(id));
    }
}
