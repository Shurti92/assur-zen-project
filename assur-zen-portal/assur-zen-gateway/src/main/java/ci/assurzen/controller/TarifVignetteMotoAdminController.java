package ci.assurzen.controller;

import ci.assurzen.dto.TarifVignetteMotoRequest;
import ci.assurzen.dto.TarifVignetteMotoResponse;
import ci.assurzen.services.TarifVignetteMotoService;
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

/**
 * Grille tarifaire des vignettes moto - reserve aux administrateurs (SecurityConfig + double garde-fou
 * TarifVignetteMotoService.requireAdmin()). Modifier un tarif n'affecte jamais une demande deja creee
 * (montants figes, cf. DemandeVignetteMoto).
 */
@RestController
@RequestMapping("${env.ws}vignettes-moto/tarifs")
@Tag(name = "Vignettes Moto - Tarifs (admin)", description = "Configuration de la grille tarifaire des vignettes moto")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TarifVignetteMotoAdminController {

    private final TarifVignetteMotoService service;

    @GetMapping
    @Operation(operationId = "listTarifsVignetteMoto", summary = "Lister tous les tarifs vignette moto (reserve aux administrateurs)")
    public ResponseEntity<List<TarifVignetteMotoResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getTarifVignetteMoto", summary = "Consulter un tarif vignette moto (reserve aux administrateurs)")
    public ResponseEntity<TarifVignetteMotoResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @Operation(operationId = "createTarifVignetteMoto", summary = "Creer un tarif vignette moto (refuse en 409 s'il chevauche un tarif deja actif pour la meme categorie)")
    public ResponseEntity<TarifVignetteMotoResponse> create(@Valid @RequestBody TarifVignetteMotoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateTarifVignetteMoto", summary = "Modifier un tarif vignette moto (n'affecte jamais les demandes deja creees)")
    public ResponseEntity<TarifVignetteMotoResponse> update(@PathVariable Long id, @Valid @RequestBody TarifVignetteMotoRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/desactiver")
    @Operation(operationId = "desactiverTarifVignetteMoto", summary = "Desactiver un tarif vignette moto")
    public ResponseEntity<TarifVignetteMotoResponse> desactiver(@PathVariable Long id) {
        return ResponseEntity.ok(service.desactiver(id));
    }
}
