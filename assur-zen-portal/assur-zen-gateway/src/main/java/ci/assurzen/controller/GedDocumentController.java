package ci.assurzen.controller;

import ci.assurzen.dto.GedDocumentLinkRequest;
import ci.assurzen.dto.GedDocumentLinkResponse;
import ci.assurzen.dto.GedDocumentResponse;
import ci.assurzen.dto.GedDocumentVersionContent;
import ci.assurzen.dto.GedDocumentVersionResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.RejectGedDocumentRequest;
import ci.assurzen.dto.UpdateGedDocumentRequest;
import ci.assurzen.entities.GedConfidentialityLevel;
import ci.assurzen.entities.GedDocumentStatus;
import ci.assurzen.services.GedDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("${env.ws}ged")
@Tag(name = "GED", description = "Gestion electronique des documents - module transversal (Phase 1 : noyau)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class GedDocumentController {

    private final GedDocumentService service;

    // ---------------------------------------------------------------- Creation / versions

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "uploadGedDocument", summary = "Deposer un nouveau document GED (reserve aux administrateurs)")
    public ResponseEntity<GedDocumentResponse> create(
            @RequestParam Long documentTypeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) GedConfidentialityLevel confidentialityLevel,
            @RequestParam(required = false) String source,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.create(documentTypeId, categoryId, title, description, entityType, entityId, confidentialityLevel, source, file));
    }

    @PostMapping(value = "/documents/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "addGedDocumentVersion", summary = "Ajouter une nouvelle version a un document existant (reserve aux administrateurs)")
    public ResponseEntity<GedDocumentVersionResponse> addVersion(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addVersion(id, file));
    }

    @GetMapping("/documents/{id}/versions")
    @Operation(operationId = "listGedDocumentVersions", summary = "Lister les versions d'un document (plus recente en premier)")
    public ResponseEntity<List<GedDocumentVersionResponse>> listVersions(@PathVariable Long id) {
        return ResponseEntity.ok(service.listVersions(id));
    }

    @GetMapping("/documents/{id}/download")
    @Operation(operationId = "downloadGedDocument", summary = "Telecharger la derniere version d'un document")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        return toDownloadResponse(service.download(id));
    }

    @GetMapping("/documents/{id}/versions/{versionId}/download")
    @Operation(operationId = "downloadGedDocumentVersion", summary = "Telecharger une version precise d'un document")
    public ResponseEntity<byte[]> downloadVersion(@PathVariable Long id, @PathVariable Long versionId) {
        return toDownloadResponse(service.downloadVersion(id, versionId));
    }

    private ResponseEntity<byte[]> toDownloadResponse(GedDocumentVersionContent content) {
        MediaType mediaType = content.contentType() != null
                ? MediaType.parseMediaType(content.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.fileName()).build().toString())
                .body(content.content());
    }

    // ---------------------------------------------------------------- Consultation / recherche

    @GetMapping("/documents/{id}")
    @Operation(operationId = "getGedDocument", summary = "Consulter les metadonnees d'un document")
    public ResponseEntity<GedDocumentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @GetMapping("/documents/search")
    @Operation(operationId = "searchGedDocuments", summary = "Rechercher des documents (pagine, filtrable)")
    public ResponseEntity<PageResponse<GedDocumentResponse>> search(
            @RequestParam(required = false) String documentNumber,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) GedDocumentStatus status,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) GedConfidentialityLevel confidentialityLevel,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.search(documentNumber, documentType, category, status, entityType, entityId,
                createdBy, dateFrom, dateTo, confidentialityLevel, page, size));
    }

    @PutMapping("/documents/{id}")
    @Operation(operationId = "updateGedDocument", summary = "Mettre a jour les metadonnees d'un document (reserve aux administrateurs)")
    public ResponseEntity<GedDocumentResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateGedDocumentRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/documents/{id}")
    @Operation(operationId = "deleteGedDocument", summary = "Supprimer (logiquement) un document (reserve aux administrateurs)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- Transitions

    @PostMapping("/documents/{id}/validate")
    @Operation(operationId = "validateGedDocument", summary = "Valider un document (reserve aux administrateurs)")
    public ResponseEntity<GedDocumentResponse> validate(@PathVariable Long id) {
        return ResponseEntity.ok(service.validate(id));
    }

    @PostMapping("/documents/{id}/reject")
    @Operation(operationId = "rejectGedDocument", summary = "Rejeter un document avec motif obligatoire (reserve aux administrateurs)")
    public ResponseEntity<GedDocumentResponse> reject(@PathVariable Long id, @Valid @RequestBody RejectGedDocumentRequest request) {
        return ResponseEntity.ok(service.reject(id, request.reason()));
    }

    @PostMapping("/documents/{id}/archive")
    @Operation(operationId = "archiveGedDocument", summary = "Archiver un document (reserve aux administrateurs)")
    public ResponseEntity<GedDocumentResponse> archive(@PathVariable Long id) {
        return ResponseEntity.ok(service.archive(id));
    }

    // ---------------------------------------------------------------- Liens metier

    @PostMapping("/documents/{id}/links")
    @Operation(operationId = "addGedDocumentLink", summary = "Rattacher un document a un objet metier (reserve aux administrateurs)")
    public ResponseEntity<GedDocumentLinkResponse> addLink(@PathVariable Long id, @Valid @RequestBody GedDocumentLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addLink(id, request));
    }

    @GetMapping("/documents/{id}/links")
    @Operation(operationId = "listGedDocumentLinks", summary = "Lister les rattachements metier d'un document")
    public ResponseEntity<List<GedDocumentLinkResponse>> listLinks(@PathVariable Long id) {
        return ResponseEntity.ok(service.listLinks(id));
    }

    @DeleteMapping("/documents/{id}/links/{linkId}")
    @Operation(operationId = "removeGedDocumentLink", summary = "Detacher un document d'un objet metier (reserve aux administrateurs)")
    public ResponseEntity<Void> removeLink(@PathVariable Long id, @PathVariable Long linkId) {
        service.removeLink(id, linkId);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- Recherche par objet metier

    @GetMapping("/clients/{id}/documents")
    @Operation(operationId = "listGedDocumentsByClient", summary = "Lister les documents rattaches a un client")
    public ResponseEntity<PageResponse<GedDocumentResponse>> byClient(@PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.listByEntity("CLIENT", id, page, size));
    }

    @GetMapping("/contracts/{id}/documents")
    @Operation(operationId = "listGedDocumentsByContract", summary = "Lister les documents rattaches a un contrat")
    public ResponseEntity<PageResponse<GedDocumentResponse>> byContract(@PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.listByEntity("CONTRACT", id, page, size));
    }

    @GetMapping("/vehicles/{id}/documents")
    @Operation(operationId = "listGedDocumentsByVehicle", summary = "Lister les documents rattaches a un vehicule")
    public ResponseEntity<PageResponse<GedDocumentResponse>> byVehicle(@PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.listByEntity("VEHICLE", id, page, size));
    }

    @GetMapping("/fleets/{id}/documents")
    @Operation(operationId = "listGedDocumentsByFleet", summary = "Lister les documents rattaches a une flotte")
    public ResponseEntity<PageResponse<GedDocumentResponse>> byFleet(@PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.listByEntity("FLEET", id, page, size));
    }
}
