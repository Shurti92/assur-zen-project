package ci.assurzen.controller;

import ci.assurzen.dto.SatisfactionImportMappingProfileRequest;
import ci.assurzen.dto.SatisfactionImportMappingProfileResponse;
import ci.assurzen.dto.SatisfactionImportMappingRequest;
import ci.assurzen.dto.SatisfactionImportMappingResponse;
import ci.assurzen.dto.SatisfactionImportMappingValueRequest;
import ci.assurzen.dto.SatisfactionImportMappingValueResponse;
import ci.assurzen.services.SatisfactionImportMappingProfileService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}satisfaction-mapping-profiles")
@Tag(name = "Satisfaction - Profils de mapping", description = "Profils de mapping CSV réutilisables (ex: 'Google Forms - Satisfaction Souscription')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SatisfactionImportMappingProfileController {

    private final SatisfactionImportMappingProfileService mappingProfileService;

    @GetMapping
    @Operation(operationId = "listSatisfactionMappingProfiles", summary = "Lister les profils de mapping")
    public ResponseEntity<List<SatisfactionImportMappingProfileResponse>> list() {
        return ResponseEntity.ok(mappingProfileService.list());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getSatisfactionMappingProfile", summary = "Détail d'un profil de mapping")
    public ResponseEntity<SatisfactionImportMappingProfileResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(mappingProfileService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createSatisfactionMappingProfile", summary = "Créer un profil de mapping")
    public ResponseEntity<SatisfactionImportMappingProfileResponse> create(@Valid @RequestBody SatisfactionImportMappingProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mappingProfileService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateSatisfactionMappingProfile", summary = "Mettre à jour un profil de mapping")
    public ResponseEntity<SatisfactionImportMappingProfileResponse> update(@PathVariable Long id, @Valid @RequestBody SatisfactionImportMappingProfileRequest request) {
        return ResponseEntity.ok(mappingProfileService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteSatisfactionMappingProfile", summary = "Supprimer (soft-delete) un profil de mapping")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        mappingProfileService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/mappings")
    @Operation(operationId = "listSatisfactionMappings", summary = "Lister les correspondances colonne CSV -> champ cible d'un profil")
    public ResponseEntity<List<SatisfactionImportMappingResponse>> listMappings(@PathVariable Long id) {
        return ResponseEntity.ok(mappingProfileService.listMappings(id));
    }

    @PostMapping("/{id}/mappings")
    @Operation(operationId = "addSatisfactionMapping", summary = "Ajouter une correspondance colonne CSV -> champ cible")
    public ResponseEntity<SatisfactionImportMappingResponse> addMapping(@PathVariable Long id, @Valid @RequestBody SatisfactionImportMappingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mappingProfileService.addMapping(id, request));
    }

    @PutMapping("/{id}/mappings/{mappingId}")
    @Operation(operationId = "updateSatisfactionMapping", summary = "Modifier une correspondance colonne CSV -> champ cible")
    public ResponseEntity<SatisfactionImportMappingResponse> updateMapping(@PathVariable Long id, @PathVariable Long mappingId,
                                                                            @Valid @RequestBody SatisfactionImportMappingRequest request) {
        return ResponseEntity.ok(mappingProfileService.updateMapping(id, mappingId, request));
    }

    @DeleteMapping("/{id}/mappings/{mappingId}")
    @Operation(operationId = "deleteSatisfactionMapping", summary = "Supprimer une correspondance colonne CSV -> champ cible")
    public ResponseEntity<Void> deleteMapping(@PathVariable Long id, @PathVariable Long mappingId) {
        mappingProfileService.deleteMapping(id, mappingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/mappings/{mappingId}/values")
    @Operation(operationId = "listSatisfactionMappingValues", summary = "Lister la table de correspondance texte brut -> valeur cible d'un mapping (ex: pour SCORE)")
    public ResponseEntity<List<SatisfactionImportMappingValueResponse>> listMappingValues(@PathVariable Long id, @PathVariable Long mappingId) {
        return ResponseEntity.ok(mappingProfileService.listMappingValues(id, mappingId));
    }

    @PostMapping("/{id}/mappings/{mappingId}/values")
    @Operation(operationId = "addSatisfactionMappingValue", summary = "Ajouter une correspondance texte brut -> valeur cible (ex: 'Oui, certainement' -> '9')")
    public ResponseEntity<SatisfactionImportMappingValueResponse> addMappingValue(@PathVariable Long id, @PathVariable Long mappingId,
                                                                                   @Valid @RequestBody SatisfactionImportMappingValueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mappingProfileService.addMappingValue(id, mappingId, request));
    }

    @PutMapping("/{id}/mappings/{mappingId}/values/{valueId}")
    @Operation(operationId = "updateSatisfactionMappingValue", summary = "Modifier une correspondance texte brut -> valeur cible")
    public ResponseEntity<SatisfactionImportMappingValueResponse> updateMappingValue(@PathVariable Long id, @PathVariable Long mappingId,
                                                                                       @PathVariable Long valueId,
                                                                                       @Valid @RequestBody SatisfactionImportMappingValueRequest request) {
        return ResponseEntity.ok(mappingProfileService.updateMappingValue(id, mappingId, valueId, request));
    }

    @DeleteMapping("/{id}/mappings/{mappingId}/values/{valueId}")
    @Operation(operationId = "deleteSatisfactionMappingValue", summary = "Supprimer une correspondance texte brut -> valeur cible")
    public ResponseEntity<Void> deleteMappingValue(@PathVariable Long id, @PathVariable Long mappingId, @PathVariable Long valueId) {
        mappingProfileService.deleteMappingValue(id, mappingId, valueId);
        return ResponseEntity.noContent().build();
    }
}
