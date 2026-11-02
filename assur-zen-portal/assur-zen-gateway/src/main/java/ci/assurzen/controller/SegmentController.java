package ci.assurzen.controller;

import ci.assurzen.dto.InsuredResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.SegmentMembreResponse;
import ci.assurzen.dto.SegmentMembresRequest;
import ci.assurzen.dto.SegmentPatchRequest;
import ci.assurzen.dto.SegmentRequest;
import ci.assurzen.dto.SegmentResponse;
import ci.assurzen.dto.SegmentRuleRequest;
import ci.assurzen.dto.SegmentRuleResponse;
import ci.assurzen.entities.SegmentType;
import ci.assurzen.services.SegmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}segments")
@Tag(name = "Segments", description = "Segments dynamiques/statiques de clients (groupes de ciblage marketing)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SegmentController {

    private final SegmentService segmentService;

    @GetMapping
    @Operation(operationId = "listSegments", summary = "Lister/rechercher les segments (paginé, filtrable par type/actif)")
    public ResponseEntity<PageResponse<SegmentResponse>> list(
            @RequestParam(required = false) SegmentType type,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(segmentService.search(type, active, null, page, size));
    }

    @GetMapping("/search")
    @Operation(operationId = "searchSegments", summary = "Recherche libre multicritère sur les segments (code/nom/description)")
    public ResponseEntity<PageResponse<SegmentResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SegmentType type,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(segmentService.search(type, active, q, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getSegment", summary = "Détail d'un segment")
    public ResponseEntity<SegmentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(segmentService.get(id));
    }

    @GetMapping("/{id}/resolve")
    @Operation(operationId = "resolveSegment", summary = "Prévisualiser les assurés qui appartiennent actuellement à ce segment")
    public ResponseEntity<List<InsuredResponse>> resolve(@PathVariable Long id) {
        return ResponseEntity.ok(segmentService.resolve(id));
    }

    @PostMapping
    @Operation(operationId = "createSegment", summary = "Créer un segment")
    public ResponseEntity<SegmentResponse> create(@Valid @RequestBody SegmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(segmentService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateSegment", summary = "Mettre à jour un segment (remplacement complet)")
    public ResponseEntity<SegmentResponse> update(@PathVariable Long id, @Valid @RequestBody SegmentRequest request) {
        return ResponseEntity.ok(segmentService.update(id, request));
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "patchSegment", summary = "Mettre à jour partiellement un segment")
    public ResponseEntity<SegmentResponse> patch(@PathVariable Long id, @Valid @RequestBody SegmentPatchRequest request) {
        return ResponseEntity.ok(segmentService.patch(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteSegment", summary = "Supprimer (soft-delete) un segment")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        segmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/enable")
    @Operation(operationId = "enableSegment", summary = "Activer un segment")
    public ResponseEntity<SegmentResponse> enable(@PathVariable Long id) {
        return ResponseEntity.ok(segmentService.enable(id));
    }

    @PatchMapping("/{id}/disable")
    @Operation(operationId = "disableSegment", summary = "Désactiver un segment")
    public ResponseEntity<SegmentResponse> disable(@PathVariable Long id) {
        return ResponseEntity.ok(segmentService.disable(id));
    }

    @GetMapping("/{id}/rules")
    @Operation(operationId = "listSegmentRules", summary = "Lister les règles d'un segment dynamique")
    public ResponseEntity<List<SegmentRuleResponse>> listRules(@PathVariable Long id) {
        return ResponseEntity.ok(segmentService.listRules(id));
    }

    @PostMapping("/{id}/rules")
    @Operation(operationId = "addSegmentRule", summary = "Ajouter une règle à un segment dynamique")
    public ResponseEntity<SegmentRuleResponse> addRule(@PathVariable Long id, @Valid @RequestBody SegmentRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(segmentService.addRule(id, request));
    }

    @PutMapping("/{id}/rules/{ruleId}")
    @Operation(operationId = "updateSegmentRule", summary = "Modifier une règle d'un segment dynamique")
    public ResponseEntity<SegmentRuleResponse> updateRule(@PathVariable Long id, @PathVariable Long ruleId,
                                                           @Valid @RequestBody SegmentRuleRequest request) {
        return ResponseEntity.ok(segmentService.updateRule(id, ruleId, request));
    }

    @DeleteMapping("/{id}/rules/{ruleId}")
    @Operation(operationId = "deleteSegmentRule", summary = "Supprimer une règle d'un segment dynamique")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id, @PathVariable Long ruleId) {
        segmentService.deleteRule(id, ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/membres")
    @Operation(operationId = "listSegmentMembres", summary = "Lister les membres d'un segment statique")
    public ResponseEntity<List<SegmentMembreResponse>> listMembres(@PathVariable Long id) {
        return ResponseEntity.ok(segmentService.listMembres(id));
    }

    @PutMapping("/{id}/membres")
    @Operation(operationId = "replaceSegmentMembres", summary = "Remplacer intégralement la liste des membres d'un segment statique")
    public ResponseEntity<List<SegmentMembreResponse>> replaceMembres(@PathVariable Long id, @Valid @RequestBody SegmentMembresRequest request) {
        return ResponseEntity.ok(segmentService.replaceMembres(id, request));
    }
}
