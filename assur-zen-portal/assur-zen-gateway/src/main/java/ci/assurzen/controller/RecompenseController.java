package ci.assurzen.controller;

import ci.assurzen.dto.RecompenseRequest;
import ci.assurzen.dto.RecompenseResponse;
import ci.assurzen.services.RecompenseService;
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
@RequestMapping("${env.ws}fidelite/recompenses")
@Tag(name = "Fidelite - Recompenses", description = "Catalogue des recompenses echangeables contre des points de fidelite")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class RecompenseController {

    private final RecompenseService recompenseService;

    @GetMapping
    @Operation(operationId = "listRecompenses", summary = "Lister le catalogue de recompenses")
    public ResponseEntity<List<RecompenseResponse>> list() {
        return ResponseEntity.ok(recompenseService.list());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getRecompense", summary = "Detail d'une recompense")
    public ResponseEntity<RecompenseResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(recompenseService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createRecompense", summary = "Ajouter une recompense au catalogue (reserve aux administrateurs/managers)")
    public ResponseEntity<RecompenseResponse> create(@Valid @RequestBody RecompenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recompenseService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateRecompense", summary = "Mettre a jour une recompense (reserve aux administrateurs/managers)")
    public ResponseEntity<RecompenseResponse> update(@PathVariable Long id, @Valid @RequestBody RecompenseRequest request) {
        return ResponseEntity.ok(recompenseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteRecompense", summary = "Retirer une recompense du catalogue (reserve aux administrateurs/managers)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recompenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
