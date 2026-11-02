package ci.assurzen.controller;

import ci.assurzen.dto.PalierFideliteRequest;
import ci.assurzen.dto.PalierFideliteResponse;
import ci.assurzen.services.PalierFideliteService;
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
@RequestMapping("${env.ws}fidelite/paliers")
@Tag(name = "Fidelite - Paliers", description = "Referentiel des paliers de fidelite (BRONZE/ARGENT/OR/PLATINE...)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PalierFideliteController {

    private final PalierFideliteService palierFideliteService;

    @GetMapping
    @Operation(operationId = "listPaliersFidelite", summary = "Lister les paliers de fidelite actifs")
    public ResponseEntity<List<PalierFideliteResponse>> list() {
        return ResponseEntity.ok(palierFideliteService.list());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getPalierFidelite", summary = "Detail d'un palier de fidelite")
    public ResponseEntity<PalierFideliteResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(palierFideliteService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createPalierFidelite", summary = "Creer un palier de fidelite (reserve aux administrateurs)")
    public ResponseEntity<PalierFideliteResponse> create(@Valid @RequestBody PalierFideliteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(palierFideliteService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updatePalierFidelite", summary = "Mettre a jour un palier de fidelite (reserve aux administrateurs)")
    public ResponseEntity<PalierFideliteResponse> update(@PathVariable Long id, @Valid @RequestBody PalierFideliteRequest request) {
        return ResponseEntity.ok(palierFideliteService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deletePalierFidelite", summary = "Desactiver un palier de fidelite (reserve aux administrateurs)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        palierFideliteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
