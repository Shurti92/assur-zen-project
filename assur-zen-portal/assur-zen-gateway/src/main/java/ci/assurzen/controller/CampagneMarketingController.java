package ci.assurzen.controller;

import ci.assurzen.dto.CampagneDestinataireResponse;
import ci.assurzen.dto.CampagneLancementRequest;
import ci.assurzen.dto.CampagneMarketingRequest;
import ci.assurzen.dto.CampagneMarketingResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.CampagneStatut;
import ci.assurzen.entities.CampagneType;
import ci.assurzen.services.CampagneMarketingService;
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
@RequestMapping("${env.ws}campagnes")
@Tag(name = "Campagnes Marketing", description = "Creation, ciblage et lancement des campagnes marketing (SMS/Email)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CampagneMarketingController {

    private final CampagneMarketingService campagneMarketingService;

    @GetMapping
    @Operation(operationId = "listCampagnes", summary = "Lister/rechercher les campagnes marketing (paginé, filtrable par statut/type)")
    public ResponseEntity<PageResponse<CampagneMarketingResponse>> list(
            @RequestParam(required = false) CampagneStatut statut,
            @RequestParam(required = false) CampagneType type,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(campagneMarketingService.search(statut, type, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getCampagne", summary = "Detail d'une campagne marketing")
    public ResponseEntity<CampagneMarketingResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(campagneMarketingService.get(id));
    }

    @GetMapping("/{id}/destinataires")
    @Operation(operationId = "listCampagneDestinataires", summary = "Lister les destinataires d'une campagne et le statut d'envoi par canal")
    public ResponseEntity<List<CampagneDestinataireResponse>> destinataires(@PathVariable Long id) {
        return ResponseEntity.ok(campagneMarketingService.listDestinataires(id));
    }

    @PostMapping
    @Operation(operationId = "createCampagne", summary = "Creer une campagne marketing (brouillon, reserve ADMIN/MARKETING)")
    public ResponseEntity<CampagneMarketingResponse> create(@Valid @RequestBody CampagneMarketingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campagneMarketingService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateCampagne", summary = "Mettre a jour une campagne en brouillon (reserve ADMIN/MARKETING)")
    public ResponseEntity<CampagneMarketingResponse> update(@PathVariable Long id, @Valid @RequestBody CampagneMarketingRequest request) {
        return ResponseEntity.ok(campagneMarketingService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteCampagne", summary = "Supprimer une campagne en brouillon (reserve ADMIN/MARKETING)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        campagneMarketingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/lancer")
    @Operation(operationId = "lancerCampagne", summary = "Resoudre les destinataires selon la cible et lancer l'envoi (asynchrone, reserve ADMIN/MARKETING)")
    public ResponseEntity<CampagneMarketingResponse> lancer(@PathVariable Long id, @RequestBody(required = false) CampagneLancementRequest request) {
        return ResponseEntity.ok(campagneMarketingService.lancer(id, request));
    }

    @PostMapping("/{id}/annuler")
    @Operation(operationId = "annulerCampagne", summary = "Annuler une campagne (reserve ADMIN/MARKETING)")
    public ResponseEntity<CampagneMarketingResponse> annuler(@PathVariable Long id) {
        return ResponseEntity.ok(campagneMarketingService.annuler(id));
    }
}
