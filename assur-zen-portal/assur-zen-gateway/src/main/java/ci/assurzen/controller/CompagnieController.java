package ci.assurzen.controller;

import ci.assurzen.dto.CompagnieRequest;
import ci.assurzen.dto.CompagnieResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.services.CompagnieService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${env.ws}compagnies")
@Tag(name = "Compagnies", description = "CRUD des compagnies d'assurance")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CompagnieController {

    private final CompagnieService service;

    @GetMapping
    @Operation(operationId = "listCompagnies", summary = "Lister/rechercher les compagnies (paginé)")
    public ResponseEntity<PageResponse<CompagnieResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.search(q, active, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getCompagnie", summary = "Consulter une compagnie")
    public ResponseEntity<CompagnieResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(operationId = "createCompagnie", summary = "Creer une compagnie")
    public ResponseEntity<CompagnieResponse> create(@Valid @RequestBody CompagnieRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateCompagnie", summary = "Mettre a jour une compagnie")
    public ResponseEntity<CompagnieResponse> update(@PathVariable Long id, @Valid @RequestBody CompagnieRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteCompagnie", summary = "Supprimer une compagnie")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
