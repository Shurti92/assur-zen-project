package ci.assurzen.controller;

import ci.assurzen.dto.ExerciceComptableRequest;
import ci.assurzen.dto.ExerciceComptableResponse;
import ci.assurzen.dto.MoisComptableResponse;
import ci.assurzen.dto.ObjectifGlobalRequest;
import ci.assurzen.services.ExerciceComptableService;
import ci.assurzen.services.MoisComptableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}exercices-comptables")
@Tag(name = "Exercices comptables", description = "CRUD des exercices comptables")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ExerciceComptableController {

    private final ExerciceComptableService service;
    private final MoisComptableService moisComptableService;

    @GetMapping
    @Operation(operationId = "listExercicesComptables", summary = "Lister les exercices comptables")
    public ResponseEntity<List<ExerciceComptableResponse>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/actifs")
    @Operation(operationId = "listExercicesComptablesActifs", summary = "Lister uniquement les exercices comptables actifs")
    public ResponseEntity<List<ExerciceComptableResponse>> listActifs() {
        return ResponseEntity.ok(service.findAllActifs());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getExerciceComptable", summary = "Consulter un exercice comptable")
    public ResponseEntity<ExerciceComptableResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(operationId = "createExerciceComptable", summary = "Creer un exercice comptable")
    public ResponseEntity<ExerciceComptableResponse> create(@Valid @RequestBody ExerciceComptableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateExerciceComptable", summary = "Mettre a jour un exercice comptable")
    public ResponseEntity<ExerciceComptableResponse> update(@PathVariable Long id, @Valid @RequestBody ExerciceComptableRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteExerciceComptable", summary = "Supprimer un exercice comptable")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cloturer")
    @Operation(operationId = "cloturerExerciceComptable", summary = "Cloturer un exercice comptable (verrouillage definitif, plus aucune ecriture possible)")
    public ResponseEntity<ExerciceComptableResponse> cloturer(@PathVariable Long id) {
        return ResponseEntity.ok(service.cloturer(id));
    }

    @PostMapping("/{id}/reouvrir")
    @Operation(operationId = "reouvrirExerciceComptable", summary = "Reouvrir un exercice comptable precedemment cloture")
    public ResponseEntity<ExerciceComptableResponse> reouvrir(@PathVariable Long id) {
        return ResponseEntity.ok(service.reouvrir(id));
    }

    @PatchMapping("/{id}/objectif")
    @Operation(operationId = "definirObjectifGlobalExerciceComptable", summary = "Definir/mettre a jour l'objectif de production global de l'exercice comptable")
    public ResponseEntity<ExerciceComptableResponse> definirObjectifGlobal(@PathVariable Long id, @Valid @RequestBody ObjectifGlobalRequest request) {
        return ResponseEntity.ok(service.definirObjectifGlobal(id, request.objectifGlobal()));
    }

    @GetMapping("/{id}/mois")
    @Operation(operationId = "listMoisComptables", summary = "Lister les mois comptables d'un exercice (Janvier..Decembre pour un exercice calendaire)")
    public ResponseEntity<List<MoisComptableResponse>> listMois(@PathVariable Long id) {
        return ResponseEntity.ok(moisComptableService.listByExercice(id));
    }

    @PostMapping("/{id}/mois/{moisId}/cloturer")
    @Operation(operationId = "cloturerMoisComptable", summary = "Cloturer un mois comptable (structurel - ne bloque pas la saisie d'ecritures, seule la cloture de l'exercice le fait)")
    public ResponseEntity<MoisComptableResponse> cloturerMois(@PathVariable Long id, @PathVariable Long moisId) {
        return ResponseEntity.ok(moisComptableService.cloturer(id, moisId));
    }

    @PostMapping("/{id}/mois/{moisId}/reouvrir")
    @Operation(operationId = "reouvrirMoisComptable", summary = "Reouvrir un mois comptable precedemment cloture")
    public ResponseEntity<MoisComptableResponse> reouvrirMois(@PathVariable Long id, @PathVariable Long moisId) {
        return ResponseEntity.ok(moisComptableService.reouvrir(id, moisId));
    }
}
