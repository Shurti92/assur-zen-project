package ci.assurzen.controller;

import ci.assurzen.dto.TemplateCategoryRequest;
import ci.assurzen.dto.TemplateCategoryResponse;
import ci.assurzen.services.TemplateCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}template-categories")
@Tag(name = "Catégories de template", description = "Référentiel des catégories de templates de communication")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TemplateCategoryController {

    private final TemplateCategoryService templateCategoryService;

    @GetMapping
    @Operation(operationId = "listTemplateCategories", summary = "Lister les catégories de template")
    public ResponseEntity<List<TemplateCategoryResponse>> list() {
        return ResponseEntity.ok(templateCategoryService.list());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getTemplateCategory", summary = "Détail d'une catégorie de template")
    public ResponseEntity<TemplateCategoryResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(templateCategoryService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createTemplateCategory", summary = "Créer une catégorie de template")
    public ResponseEntity<TemplateCategoryResponse> create(@Valid @RequestBody TemplateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateCategoryService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateTemplateCategory", summary = "Mettre à jour une catégorie de template")
    public ResponseEntity<TemplateCategoryResponse> update(@PathVariable Long id, @Valid @RequestBody TemplateCategoryRequest request) {
        return ResponseEntity.ok(templateCategoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteTemplateCategory", summary = "Supprimer (soft-delete) une catégorie de template")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        templateCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
