package ci.assurzen.controller;

import ci.assurzen.dto.TemplateVariableRequest;
import ci.assurzen.dto.TemplateVariableResponse;
import ci.assurzen.services.TemplateVariableService;
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
@RequestMapping("${env.ws}template-variables")
@Tag(name = "Variables de template", description = "Catalogue des variables {{...}} utilisables dans les templates de communication")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TemplateVariableController {

    private final TemplateVariableService templateVariableService;

    @GetMapping
    @Operation(operationId = "listTemplateVariables", summary = "Lister le catalogue de variables")
    public ResponseEntity<List<TemplateVariableResponse>> list() {
        return ResponseEntity.ok(templateVariableService.list());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getTemplateVariable", summary = "Détail d'une variable")
    public ResponseEntity<TemplateVariableResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(templateVariableService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createTemplateVariable", summary = "Créer une variable")
    public ResponseEntity<TemplateVariableResponse> create(@Valid @RequestBody TemplateVariableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateVariableService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateTemplateVariable", summary = "Mettre à jour une variable")
    public ResponseEntity<TemplateVariableResponse> update(@PathVariable Long id, @Valid @RequestBody TemplateVariableRequest request) {
        return ResponseEntity.ok(templateVariableService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteTemplateVariable", summary = "Supprimer (soft-delete) une variable")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        templateVariableService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
