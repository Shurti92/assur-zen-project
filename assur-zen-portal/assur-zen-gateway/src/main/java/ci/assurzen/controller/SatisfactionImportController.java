package ci.assurzen.controller;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.SatisfactionImportErrorResponse;
import ci.assurzen.dto.SatisfactionImportReportResponse;
import ci.assurzen.dto.SatisfactionImportResponse;
import ci.assurzen.entities.DuplicateStrategy;
import ci.assurzen.entities.SatisfactionImportStatut;
import ci.assurzen.services.SatisfactionImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("${env.ws}satisfaction-imports")
@Tag(name = "Satisfaction - Imports CSV", description = "Import en masse des reponses de satisfaction depuis un fichier CSV (ex: export Google Forms)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SatisfactionImportController {

    private final SatisfactionImportService satisfactionImportService;

    @GetMapping
    @Operation(operationId = "listSatisfactionImports", summary = "Lister/rechercher les imports (paginé, filtres date/statut/utilisateur/enquête/source)")
    public ResponseEntity<PageResponse<SatisfactionImportResponse>> list(
            @RequestParam(required = false) SatisfactionImportStatut statut,
            @RequestParam(required = false) Long mappingProfileId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String surveyId,
            @RequestParam(required = false) Long executeParId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFin,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(satisfactionImportService.search(statut, mappingProfileId, source, surveyId, executeParId, dateDebut, dateFin, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getSatisfactionImport", summary = "Détail d'un import")
    public ResponseEntity<SatisfactionImportResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(satisfactionImportService.get(id));
    }

    @PostMapping(consumes = "multipart/form-data")
    @Operation(operationId = "uploadSatisfactionImport", summary = "Uploader un fichier CSV de réponses de satisfaction et (optionnellement) lancer son traitement")
    public ResponseEntity<SatisfactionImportResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long mappingProfileId,
            @RequestParam(required = false) String surveyId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false, defaultValue = ";") String delimiter,
            @RequestParam(required = false, defaultValue = "UTF-8") String encoding,
            @RequestParam(required = false, defaultValue = "true") Boolean hasHeader,
            @RequestParam(required = false, defaultValue = "IGNORER") DuplicateStrategy duplicateStrategy,
            @RequestParam(required = false, defaultValue = "true") Boolean executeImmediately) {
        return ResponseEntity.status(HttpStatus.CREATED).body(satisfactionImportService.upload(
                file, mappingProfileId, surveyId, source, delimiter, encoding, hasHeader, duplicateStrategy, executeImmediately));
    }

    @PostMapping("/{id}/retry")
    @Operation(operationId = "retrySatisfactionImport", summary = "Relancer le traitement d'un import (efface les erreurs précédentes et rejoue le fichier)")
    public ResponseEntity<SatisfactionImportResponse> retry(@PathVariable Long id) {
        return ResponseEntity.ok(satisfactionImportService.retry(id));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteSatisfactionImport", summary = "Supprimer (soft-delete) un import")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        satisfactionImportService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/errors")
    @Operation(operationId = "getSatisfactionImportErrors", summary = "Lister les erreurs ligne par ligne d'un import")
    public ResponseEntity<List<SatisfactionImportErrorResponse>> errors(@PathVariable Long id) {
        return ResponseEntity.ok(satisfactionImportService.listErrors(id));
    }

    @GetMapping("/{id}/report")
    @Operation(operationId = "getSatisfactionImportReport", summary = "Rapport d'import : statistiques, répartition NPS, erreurs par type")
    public ResponseEntity<SatisfactionImportReportResponse> report(@PathVariable Long id) {
        return ResponseEntity.ok(satisfactionImportService.report(id));
    }
}
