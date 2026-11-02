package ci.assurzen.controller;

import ci.assurzen.dto.RegleRecompenseParrainageRequest;
import ci.assurzen.dto.RegleRecompenseParrainageResponse;
import ci.assurzen.services.RegleRecompenseParrainageService;
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

import java.util.List;

@RestController
@RequestMapping("${env.ws}parrainage/regles-recompense")
@Tag(name = "Parrainage - Regles de recompense", description = "Regles configurables definissant qui recoit quoi (parrain/filleul/les deux) dans un programme de parrainage")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class RegleRecompenseParrainageController {

    private final RegleRecompenseParrainageService regleRecompenseParrainageService;

    @GetMapping
    @Operation(operationId = "listReglesRecompenseParrainage", summary = "Lister les regles de recompense (filtrable par programme)")
    public ResponseEntity<List<RegleRecompenseParrainageResponse>> list(@RequestParam(required = false) Long programmeId) {
        return ResponseEntity.ok(regleRecompenseParrainageService.list(programmeId));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getRegleRecompenseParrainage", summary = "Detail d'une regle de recompense")
    public ResponseEntity<RegleRecompenseParrainageResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(regleRecompenseParrainageService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createRegleRecompenseParrainage", summary = "Creer une regle de recompense (reserve aux administrateurs/managers)")
    public ResponseEntity<RegleRecompenseParrainageResponse> create(@Valid @RequestBody RegleRecompenseParrainageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(regleRecompenseParrainageService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateRegleRecompenseParrainage", summary = "Mettre a jour une regle de recompense (reserve aux administrateurs/managers)")
    public ResponseEntity<RegleRecompenseParrainageResponse> update(@PathVariable Long id, @Valid @RequestBody RegleRecompenseParrainageRequest request) {
        return ResponseEntity.ok(regleRecompenseParrainageService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteRegleRecompenseParrainage", summary = "Desactiver une regle de recompense (reserve aux administrateurs/managers)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        regleRecompenseParrainageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
