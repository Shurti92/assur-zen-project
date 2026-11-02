package ci.assurzen.controller;

import ci.assurzen.dto.CodeParrainageRequest;
import ci.assurzen.dto.CodeParrainageResponse;
import ci.assurzen.services.CodeParrainageService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}parrainage/codes")
@Tag(name = "Parrainage - Codes", description = "Generation et validation des codes/liens de parrainage reutilisables")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CodeParrainageController {

    private final CodeParrainageService codeParrainageService;

    @PostMapping
    @Operation(operationId = "obtenirOuCreerCodeParrainage", summary = "Obtenir le code actif d'un parrain pour un programme, ou en creer un nouveau")
    public ResponseEntity<CodeParrainageResponse> obtenirOuCreer(@Valid @RequestBody CodeParrainageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(codeParrainageService.obtenirOuCreer(request));
    }

    @GetMapping("/{code}")
    @Operation(operationId = "getCodeParrainage", summary = "Detail d'un code de parrainage")
    public ResponseEntity<CodeParrainageResponse> get(@PathVariable String code) {
        return ResponseEntity.ok(codeParrainageService.getByCode(code));
    }

    @PostMapping("/{code}/valider")
    @Operation(operationId = "validerCodeParrainage", summary = "Verifier qu'un code est utilisable (actif, non expire, quota non atteint, programme actif)")
    public ResponseEntity<CodeParrainageResponse> valider(@PathVariable String code) {
        return ResponseEntity.ok(codeParrainageService.valider(code));
    }

    @PatchMapping("/{id}/disable")
    @Operation(operationId = "disableCodeParrainage", summary = "Desactiver un code de parrainage")
    public ResponseEntity<CodeParrainageResponse> disable(@PathVariable Long id) {
        return ResponseEntity.ok(codeParrainageService.disable(id));
    }

    @GetMapping
    @Operation(operationId = "listCodesParrainageParParrain", summary = "Lister les codes de parrainage d'un parrain")
    public ResponseEntity<List<CodeParrainageResponse>> listByParrain(@RequestParam Long parrainId) {
        return ResponseEntity.ok(codeParrainageService.listByParrain(parrainId));
    }
}
