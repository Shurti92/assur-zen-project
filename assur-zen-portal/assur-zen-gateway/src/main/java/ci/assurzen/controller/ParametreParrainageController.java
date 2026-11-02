package ci.assurzen.controller;

import ci.assurzen.dto.ParametreParrainageRequest;
import ci.assurzen.dto.ParametreParrainageResponse;
import ci.assurzen.services.ParametreParrainageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}parrainage/parametres")
@Tag(name = "Parrainage - Parametres", description = "Parametres cle/valeur du moteur de parrainage")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ParametreParrainageController {

    private final ParametreParrainageService parametreParrainageService;

    @GetMapping
    @Operation(operationId = "listParametresParrainage", summary = "Lister les parametres du moteur de parrainage")
    public ResponseEntity<List<ParametreParrainageResponse>> list() {
        return ResponseEntity.ok(parametreParrainageService.list());
    }

    @PutMapping
    @Operation(operationId = "upsertParametreParrainage", summary = "Creer ou mettre a jour un parametre (reserve aux administrateurs/managers)")
    public ResponseEntity<ParametreParrainageResponse> upsert(@Valid @RequestBody ParametreParrainageRequest request) {
        return ResponseEntity.ok(parametreParrainageService.upsert(request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteParametreParrainage", summary = "Supprimer un parametre (reserve aux administrateurs/managers)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        parametreParrainageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
