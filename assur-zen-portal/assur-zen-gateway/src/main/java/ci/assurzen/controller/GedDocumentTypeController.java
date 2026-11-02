package ci.assurzen.controller;

import ci.assurzen.dto.GedDocumentTypeRequest;
import ci.assurzen.dto.GedDocumentTypeResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.services.GedDocumentTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${env.ws}ged/document-types")
@Tag(name = "GED - Types de document", description = "Referentiel des types de document GED (CONTRAT, ATTESTATION, PIECE_IDENTITE...)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class GedDocumentTypeController {

    private final GedDocumentTypeService service;

    @GetMapping
    @Operation(operationId = "listGedDocumentTypes", summary = "Lister/rechercher les types de document GED (pagine, filtrable)")
    public ResponseEntity<PageResponse<GedDocumentTypeResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.search(q, actif, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getGedDocumentType", summary = "Detail d'un type de document GED")
    public ResponseEntity<GedDocumentTypeResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @Operation(operationId = "createGedDocumentType", summary = "Creer un type de document GED (reserve aux administrateurs)")
    public ResponseEntity<GedDocumentTypeResponse> create(@Valid @RequestBody GedDocumentTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateGedDocumentType", summary = "Mettre a jour un type de document GED (reserve aux administrateurs)")
    public ResponseEntity<GedDocumentTypeResponse> update(@PathVariable Long id, @Valid @RequestBody GedDocumentTypeRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteGedDocumentType", summary = "Supprimer un type de document GED non systeme (reserve aux administrateurs)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
