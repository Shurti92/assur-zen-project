package ci.assurzen.controller;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.VehiculeRequest;
import ci.assurzen.dto.VehiculeResponse;
import ci.assurzen.entities.VehiculeStatut;
import ci.assurzen.entities.VehiculeType;
import ci.assurzen.services.VehiculeService;
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

@RestController
@RequestMapping("${env.ws}vehicules")
@Tag(name = "Vehicules", description = "Catalogue des vehicules auto/moto (support du module Gestion de flotte)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class VehiculesController {

    private final VehiculeService vehiculeService;

    @GetMapping
    @Operation(operationId = "listVehicules", summary = "Lister/rechercher les vehicules (pagine, filtrable)")
    public ResponseEntity<PageResponse<VehiculeResponse>> list(
            @RequestParam(required = false) String immatriculation,
            @RequestParam(required = false) Long proprietaireId,
            @RequestParam(required = false) VehiculeType type,
            @RequestParam(required = false) VehiculeStatut statut,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(vehiculeService.search(immatriculation, proprietaireId, type, statut, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getVehicule", summary = "Detail d'un vehicule")
    public ResponseEntity<VehiculeResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(vehiculeService.getVehicule(id));
    }

    @PostMapping
    @Operation(operationId = "createVehicule", summary = "Creer un vehicule")
    public ResponseEntity<VehiculeResponse> create(@Valid @RequestBody VehiculeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehiculeService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateVehicule", summary = "Mettre a jour un vehicule")
    public ResponseEntity<VehiculeResponse> update(@PathVariable Long id, @Valid @RequestBody VehiculeRequest request) {
        return ResponseEntity.ok(vehiculeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteVehicule", summary = "Supprimer un vehicule non rattache a une flotte et sans contrat associe (reserve aux administrateurs)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        vehiculeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
