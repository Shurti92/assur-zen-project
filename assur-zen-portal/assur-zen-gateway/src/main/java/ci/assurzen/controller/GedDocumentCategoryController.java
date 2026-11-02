package ci.assurzen.controller;

import ci.assurzen.dto.GedDocumentCategoryRequest;
import ci.assurzen.dto.GedDocumentCategoryResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.services.GedDocumentCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${env.ws}ged/document-categories")
@Tag(name = "GED - Categories de document", description = "Referentiel des categories de document GED (CONTRACTUAL, CLIENT, CLAIM...)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class GedDocumentCategoryController {

    private final GedDocumentCategoryService service;

    @GetMapping
    @Operation(operationId = "listGedDocumentCategories", summary = "Lister/rechercher les categories de document GED (pagine, filtrable)")
    public ResponseEntity<PageResponse<GedDocumentCategoryResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.search(q, actif, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getGedDocumentCategory", summary = "Detail d'une categorie de document GED")
    public ResponseEntity<GedDocumentCategoryResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @Operation(operationId = "createGedDocumentCategory", summary = "Creer une categorie de document GED (reserve aux administrateurs)")
    public ResponseEntity<GedDocumentCategoryResponse> create(@Valid @RequestBody GedDocumentCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateGedDocumentCategory", summary = "Mettre a jour une categorie de document GED (reserve aux administrateurs)")
    public ResponseEntity<GedDocumentCategoryResponse> update(@PathVariable Long id, @Valid @RequestBody GedDocumentCategoryRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteGedDocumentCategory", summary = "Supprimer une categorie de document GED non systeme (reserve aux administrateurs)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
