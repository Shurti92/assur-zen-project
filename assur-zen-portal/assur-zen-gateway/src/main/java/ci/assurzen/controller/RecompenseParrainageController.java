package ci.assurzen.controller;

import ci.assurzen.dto.ParrainageMotifRequest;
import ci.assurzen.dto.RecompenseParrainageResponse;
import ci.assurzen.entities.StatutRecompenseParrainage;
import ci.assurzen.services.RecompenseParrainageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}parrainage/recompenses")
@Tag(name = "Parrainage - Recompenses", description = "Cycle de vie des recompenses generees par les parrainages eligibles")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class RecompenseParrainageController {

    private final RecompenseParrainageService recompenseParrainageService;

    @GetMapping
    @Operation(operationId = "listRecompensesParrainage", summary = "Lister/rechercher les recompenses de parrainage")
    public ResponseEntity<List<RecompenseParrainageResponse>> search(
            @RequestParam(required = false) StatutRecompenseParrainage statut,
            @RequestParam(required = false) Long parrainageId,
            @RequestParam(required = false) Long beneficiaireId) {
        return ResponseEntity.ok(recompenseParrainageService.search(statut, parrainageId, beneficiaireId));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getRecompenseParrainage", summary = "Detail d'une recompense")
    public ResponseEntity<RecompenseParrainageResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(recompenseParrainageService.get(id));
    }

    @PostMapping("/{id}/approuver")
    @Operation(operationId = "approuverRecompenseParrainage", summary = "Approuver une recompense en attente (reserve aux administrateurs/managers)")
    public ResponseEntity<RecompenseParrainageResponse> approuver(@PathVariable Long id) {
        return ResponseEntity.ok(recompenseParrainageService.approuver(id));
    }

    @PostMapping("/{id}/attribuer")
    @Operation(operationId = "attribuerRecompenseParrainage", summary = "Attribuer (crediter) une recompense approuvee (reserve aux administrateurs/managers)")
    public ResponseEntity<RecompenseParrainageResponse> attribuer(@PathVariable Long id) {
        return ResponseEntity.ok(recompenseParrainageService.attribuer(id));
    }

    @PostMapping("/{id}/annuler")
    @Operation(operationId = "annulerRecompenseParrainage", summary = "Annuler une recompense non finale (reserve aux administrateurs/managers)")
    public ResponseEntity<RecompenseParrainageResponse> annuler(@PathVariable Long id, @Valid @RequestBody(required = false) ParrainageMotifRequest request) {
        return ResponseEntity.ok(recompenseParrainageService.annuler(id, request == null ? null : request.motif()));
    }

    @PostMapping("/{id}/reverser")
    @Operation(operationId = "reverserRecompenseParrainage", summary = "Reverser une recompense deja attribuee, ex. suite a l'annulation du contrat du filleul (reserve aux administrateurs/managers)")
    public ResponseEntity<RecompenseParrainageResponse> reverser(@PathVariable Long id, @Valid @RequestBody(required = false) ParrainageMotifRequest request) {
        return ResponseEntity.ok(recompenseParrainageService.reverser(id, request == null ? null : request.motif()));
    }
}
