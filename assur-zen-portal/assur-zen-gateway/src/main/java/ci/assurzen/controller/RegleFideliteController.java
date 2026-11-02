package ci.assurzen.controller;

import ci.assurzen.dto.RegleFideliteRequest;
import ci.assurzen.dto.RegleFideliteResponse;
import ci.assurzen.services.RegleFideliteService;
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
@RequestMapping("${env.ws}fidelite/regles")
@Tag(name = "Fidelite - Regles VIP", description = "Moteur de regles configurable pilotant le calcul du score de fidelite / statut VIP")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class RegleFideliteController {

    private final RegleFideliteService regleFideliteService;

    @GetMapping
    @Operation(operationId = "listReglesFidelite", summary = "Lister les regles du moteur de score")
    public ResponseEntity<List<RegleFideliteResponse>> list() {
        return ResponseEntity.ok(regleFideliteService.list());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getRegleFidelite", summary = "Detail d'une regle")
    public ResponseEntity<RegleFideliteResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(regleFideliteService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createRegleFidelite", summary = "Creer une regle (reserve aux administrateurs/managers)")
    public ResponseEntity<RegleFideliteResponse> create(@Valid @RequestBody RegleFideliteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(regleFideliteService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateRegleFidelite", summary = "Mettre a jour une regle (reserve aux administrateurs/managers)")
    public ResponseEntity<RegleFideliteResponse> update(@PathVariable Long id, @Valid @RequestBody RegleFideliteRequest request) {
        return ResponseEntity.ok(regleFideliteService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteRegleFidelite", summary = "Desactiver une regle (reserve aux administrateurs/managers)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        regleFideliteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
