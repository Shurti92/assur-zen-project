package ci.assurzen.controller;

import ci.assurzen.dto.CommunicationTemplatePatchRequest;
import ci.assurzen.dto.CommunicationTemplateRequest;
import ci.assurzen.dto.CommunicationTemplateResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.TemplatePreviewRequest;
import ci.assurzen.dto.TemplatePreviewResponse;
import ci.assurzen.entities.CanalEnvoi;
import ci.assurzen.entities.TemplateStatut;
import ci.assurzen.services.CommunicationTemplateService;
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

@RestController
@RequestMapping("${env.ws}templates")
@Tag(name = "Templates de communication", description = "Gabarits de message génériques (SMS/Email/WhatsApp/Push/PDF) réutilisables par les campagnes")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CommunicationTemplateController {

    private final CommunicationTemplateService communicationTemplateService;

    @GetMapping
    @Operation(operationId = "listTemplates", summary = "Lister/rechercher les templates (paginé, filtrable par canal/statut/catégorie/actif)")
    public ResponseEntity<PageResponse<CommunicationTemplateResponse>> list(
            @RequestParam(required = false) CanalEnvoi canal,
            @RequestParam(required = false) TemplateStatut statut,
            @RequestParam(required = false) Long categorieId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(communicationTemplateService.search(canal, statut, categorieId, active, null, page, size));
    }

    @GetMapping("/search")
    @Operation(operationId = "searchTemplates", summary = "Recherche libre multicritère sur les templates (code/nom/description)")
    public ResponseEntity<PageResponse<CommunicationTemplateResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CanalEnvoi canal,
            @RequestParam(required = false) TemplateStatut statut,
            @RequestParam(required = false) Long categorieId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(communicationTemplateService.search(canal, statut, categorieId, active, q, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getTemplate", summary = "Détail d'un template")
    public ResponseEntity<CommunicationTemplateResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(communicationTemplateService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createTemplate", summary = "Créer un template (brouillon)")
    public ResponseEntity<CommunicationTemplateResponse> create(@Valid @RequestBody CommunicationTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(communicationTemplateService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateTemplate", summary = "Mettre à jour un template (remplacement complet)")
    public ResponseEntity<CommunicationTemplateResponse> update(@PathVariable Long id, @Valid @RequestBody CommunicationTemplateRequest request) {
        return ResponseEntity.ok(communicationTemplateService.update(id, request));
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "patchTemplate", summary = "Mettre à jour partiellement un template")
    public ResponseEntity<CommunicationTemplateResponse> patch(@PathVariable Long id, @Valid @RequestBody CommunicationTemplatePatchRequest request) {
        return ResponseEntity.ok(communicationTemplateService.patch(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteTemplate", summary = "Supprimer (soft-delete) un template")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        communicationTemplateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/enable")
    @Operation(operationId = "enableTemplate", summary = "Activer un template (statut ACTIF) — vérifie maxCaracteres")
    public ResponseEntity<CommunicationTemplateResponse> enable(@PathVariable Long id) {
        return ResponseEntity.ok(communicationTemplateService.enable(id));
    }

    @PatchMapping("/{id}/disable")
    @Operation(operationId = "disableTemplate", summary = "Désactiver/archiver un template")
    public ResponseEntity<CommunicationTemplateResponse> disable(@PathVariable Long id) {
        return ResponseEntity.ok(communicationTemplateService.disable(id));
    }

    @PostMapping("/{id}/dupliquer")
    @Operation(operationId = "duplicateTemplate", summary = "Dupliquer un template (nouveau code, statut BROUILLON)")
    public ResponseEntity<CommunicationTemplateResponse> duplicate(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(communicationTemplateService.duplicate(id));
    }

    @PostMapping("/{id}/apercu")
    @Operation(operationId = "previewTemplate", summary = "Prévisualiser le rendu d'un template avec des valeurs d'exemple")
    public ResponseEntity<TemplatePreviewResponse> preview(@PathVariable Long id, @RequestBody(required = false) TemplatePreviewRequest request) {
        return ResponseEntity.ok(communicationTemplateService.preview(id, request));
    }
}
