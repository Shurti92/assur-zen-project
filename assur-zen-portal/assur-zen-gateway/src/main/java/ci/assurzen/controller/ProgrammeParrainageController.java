package ci.assurzen.controller;

import ci.assurzen.dto.ProgrammeParrainageRequest;
import ci.assurzen.dto.ProgrammeParrainageResponse;
import ci.assurzen.services.ProgrammeParrainageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("${env.ws}parrainage/programmes")
@Tag(name = "Parrainage - Programmes", description = "Definition des programmes de parrainage (quotas, periode, produits eligibles)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ProgrammeParrainageController {

    private final ProgrammeParrainageService programmeParrainageService;

    @GetMapping
    @Operation(operationId = "listProgrammesParrainage", summary = "Lister les programmes de parrainage")
    public ResponseEntity<List<ProgrammeParrainageResponse>> list() {
        return ResponseEntity.ok(programmeParrainageService.list());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getProgrammeParrainage", summary = "Detail d'un programme de parrainage")
    public ResponseEntity<ProgrammeParrainageResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(programmeParrainageService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createProgrammeParrainage", summary = "Creer un programme de parrainage (reserve aux administrateurs/managers)")
    public ResponseEntity<ProgrammeParrainageResponse> create(@Valid @RequestBody ProgrammeParrainageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(programmeParrainageService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateProgrammeParrainage", summary = "Mettre a jour un programme de parrainage (reserve aux administrateurs/managers)")
    public ResponseEntity<ProgrammeParrainageResponse> update(@PathVariable Long id, @Valid @RequestBody ProgrammeParrainageRequest request) {
        return ResponseEntity.ok(programmeParrainageService.update(id, request));
    }

    @PatchMapping("/{id}/activer")
    @Operation(operationId = "activerProgrammeParrainage", summary = "Activer un programme de parrainage (reserve aux administrateurs/managers)")
    public ResponseEntity<ProgrammeParrainageResponse> activer(@PathVariable Long id) {
        return ResponseEntity.ok(programmeParrainageService.activer(id));
    }

    @PatchMapping("/{id}/suspendre")
    @Operation(operationId = "suspendreProgrammeParrainage", summary = "Suspendre un programme de parrainage (reserve aux administrateurs/managers)")
    public ResponseEntity<ProgrammeParrainageResponse> suspendre(@PathVariable Long id) {
        return ResponseEntity.ok(programmeParrainageService.suspendre(id));
    }

    @PatchMapping("/{id}/cloturer")
    @Operation(operationId = "cloturerProgrammeParrainage", summary = "Cloturer definitivement un programme de parrainage (reserve aux administrateurs/managers)")
    public ResponseEntity<ProgrammeParrainageResponse> cloturer(@PathVariable Long id) {
        return ResponseEntity.ok(programmeParrainageService.cloturer(id));
    }
}
