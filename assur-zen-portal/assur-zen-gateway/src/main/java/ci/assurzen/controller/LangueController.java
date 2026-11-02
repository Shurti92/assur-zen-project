package ci.assurzen.controller;

import ci.assurzen.dto.LangueRequest;
import ci.assurzen.dto.LangueResponse;
import ci.assurzen.services.LangueService;
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
@RequestMapping("${env.ws}langues")
@Tag(name = "Langues", description = "Référentiel des langues disponibles pour les templates de communication")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LangueController {

    private final LangueService langueService;

    @GetMapping
    @Operation(operationId = "listLangues", summary = "Lister les langues")
    public ResponseEntity<List<LangueResponse>> list() {
        return ResponseEntity.ok(langueService.list());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getLangue", summary = "Détail d'une langue")
    public ResponseEntity<LangueResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(langueService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createLangue", summary = "Créer une langue")
    public ResponseEntity<LangueResponse> create(@Valid @RequestBody LangueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(langueService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateLangue", summary = "Mettre à jour une langue")
    public ResponseEntity<LangueResponse> update(@PathVariable Long id, @Valid @RequestBody LangueRequest request) {
        return ResponseEntity.ok(langueService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteLangue", summary = "Supprimer (soft-delete) une langue")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        langueService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
