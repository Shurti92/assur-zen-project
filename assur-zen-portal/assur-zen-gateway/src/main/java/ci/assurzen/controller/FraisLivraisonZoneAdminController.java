package ci.assurzen.controller;

import ci.assurzen.dto.FraisLivraisonZoneRequest;
import ci.assurzen.dto.FraisLivraisonZoneResponse;
import ci.assurzen.services.FraisLivraisonZoneService;
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
 * Zones de livraison des vignettes moto - reserve aux administrateurs. Conception extensible (§8) :
 * ajouter une zone = un nouvel enregistrement, aucun code a modifier.
 */
@RestController
@RequestMapping("${env.ws}vignettes-moto/livraisons")
@Tag(name = "Vignettes Moto - Livraisons (admin)", description = "Configuration des zones et frais de livraison des vignettes moto")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class FraisLivraisonZoneAdminController {

    private final FraisLivraisonZoneService service;

    @GetMapping
    @Operation(operationId = "listFraisLivraisonZones", summary = "Lister toutes les zones de livraison (reserve aux administrateurs)")
    public ResponseEntity<List<FraisLivraisonZoneResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getFraisLivraisonZone", summary = "Consulter une zone de livraison (reserve aux administrateurs)")
    public ResponseEntity<FraisLivraisonZoneResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @Operation(operationId = "createFraisLivraisonZone", summary = "Creer une zone de livraison")
    public ResponseEntity<FraisLivraisonZoneResponse> create(@Valid @RequestBody FraisLivraisonZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateFraisLivraisonZone", summary = "Modifier une zone de livraison")
    public ResponseEntity<FraisLivraisonZoneResponse> update(@PathVariable Long id, @Valid @RequestBody FraisLivraisonZoneRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/desactiver")
    @Operation(operationId = "desactiverFraisLivraisonZone", summary = "Desactiver une zone de livraison")
    public ResponseEntity<FraisLivraisonZoneResponse> desactiver(@PathVariable Long id) {
        return ResponseEntity.ok(service.desactiver(id));
    }
}
