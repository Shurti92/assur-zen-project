package ci.assurzen.controller;

import ci.assurzen.dto.CommercialMonthlyObjectiveRequest;
import ci.assurzen.dto.CommercialMonthlyObjectiveResponse;
import ci.assurzen.dto.CommercialMonthlyObjectiveRevisionRequest;
import ci.assurzen.dto.CommercialObjectiveRequest;
import ci.assurzen.dto.CommercialObjectiveResponse;
import ci.assurzen.services.CommercialMonthlyObjectiveService;
import ci.assurzen.services.CommercialObjectiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}commercials/{commercialId}")
@Tag(name = "Objectifs commerciaux", description = "Objectifs global (annuel) et mensuels d'un commercial")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CommercialObjectiveController {

    private final CommercialObjectiveService objectiveService;
    private final CommercialMonthlyObjectiveService monthlyObjectiveService;

    @GetMapping("/objectives")
    @Operation(operationId = "listCommercialObjectives", summary = "Lister les objectifs globaux (annuels) d'un commercial")
    public ResponseEntity<List<CommercialObjectiveResponse>> listObjectives(@PathVariable Long commercialId) {
        return ResponseEntity.ok(objectiveService.list(commercialId));
    }

    @PostMapping("/objectives")
    @Operation(operationId = "createCommercialObjective", summary = "Creer l'objectif global (annuel) d'un commercial pour un exercice comptable (reserve aux administrateurs)")
    public ResponseEntity<CommercialObjectiveResponse> createObjective(@PathVariable Long commercialId, @Valid @RequestBody CommercialObjectiveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(objectiveService.create(commercialId, request));
    }

    @PutMapping("/objectives/{objectiveId}")
    @Operation(operationId = "updateCommercialObjective", summary = "Modifier l'objectif global d'un commercial (reserve aux administrateurs)")
    public ResponseEntity<CommercialObjectiveResponse> updateObjective(@PathVariable Long commercialId, @PathVariable Long objectiveId, @Valid @RequestBody CommercialObjectiveRequest request) {
        return ResponseEntity.ok(objectiveService.update(commercialId, objectiveId, request));
    }

    @GetMapping("/monthly-objectives")
    @Operation(operationId = "listCommercialMonthlyObjectives", summary = "Lister les objectifs mensuels d'un commercial")
    public ResponseEntity<List<CommercialMonthlyObjectiveResponse>> listMonthlyObjectives(@PathVariable Long commercialId) {
        return ResponseEntity.ok(monthlyObjectiveService.list(commercialId));
    }

    @PostMapping("/monthly-objectives")
    @Operation(operationId = "createCommercialMonthlyObjective", summary = "Creer l'objectif d'un mois comptable pour un commercial (reserve aux administrateurs, refuse en 409 si deja existant - utiliser la revision)")
    public ResponseEntity<CommercialMonthlyObjectiveResponse> createMonthlyObjective(@PathVariable Long commercialId, @Valid @RequestBody CommercialMonthlyObjectiveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(monthlyObjectiveService.create(commercialId, request));
    }

    @PutMapping("/monthly-objectives/{objectiveId}")
    @Operation(operationId = "reviserCommercialMonthlyObjective", summary = "Reviser un objectif mensuel existant (motif obligatoire, trace dans l'audit fonctionnel - reserve aux administrateurs)")
    public ResponseEntity<CommercialMonthlyObjectiveResponse> reviserMonthlyObjective(@PathVariable Long commercialId, @PathVariable Long objectiveId, @Valid @RequestBody CommercialMonthlyObjectiveRevisionRequest request) {
        return ResponseEntity.ok(monthlyObjectiveService.reviser(commercialId, objectiveId, request));
    }
}
