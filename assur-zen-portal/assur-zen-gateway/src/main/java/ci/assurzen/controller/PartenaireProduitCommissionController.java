package ci.assurzen.controller;

import ci.assurzen.dto.PartenaireProduitCommissionRequest;
import ci.assurzen.dto.PartenaireProduitCommissionResponse;
import ci.assurzen.entities.BeneficiaireCommission;
import ci.assurzen.services.PartenaireProduitCommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/** Commissions configurees en montant FCFA par produit + partenaire (ou type de partenaire) ou
 * commercial - ecritures reservees aux administrateurs (SecurityConfig). */
@RestController
@RequestMapping("${env.ws}partner-commissions")
@Tag(name = "Commissions configurees", description = "Commission partenaire / commerciale configuree par produit (montant FCFA)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PartenaireProduitCommissionController {

    private final PartenaireProduitCommissionService service;

    @GetMapping
    @Operation(operationId = "listPartnerCommissions", summary = "Lister/filtrer les commissions configurees")
    public ResponseEntity<List<PartenaireProduitCommissionResponse>> list(@RequestParam(required = false) Long produitId,
                                                                          @RequestParam(required = false) BeneficiaireCommission beneficiaire,
                                                                          @RequestParam(required = false) Long partenaireId,
                                                                          @RequestParam(required = false) Long commercialId) {
        return ResponseEntity.ok(service.findAll(produitId, beneficiaire, partenaireId, commercialId));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getPartnerCommission", summary = "Consulter une commission configuree")
    public ResponseEntity<PartenaireProduitCommissionResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/by-product/{productId}/partner/{partnerId}")
    @Operation(operationId = "resolvePartnerCommission", summary = "Commission partenaire applicable (partenaire precis, sinon son type) - 404 si aucune")
    public ResponseEntity<PartenaireProduitCommissionResponse> forPartner(@PathVariable Long productId, @PathVariable Long partnerId,
                                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.findForPartenaire(productId, partnerId, date));
    }

    @GetMapping("/by-product/{productId}/commercial/{commercialId}")
    @Operation(operationId = "resolveCommercialCommission", summary = "Commission configuree pour ce commercial precis - 404 si aucune")
    public ResponseEntity<PartenaireProduitCommissionResponse> forCommercial(@PathVariable Long productId, @PathVariable Long commercialId,
                                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.findForCommercial(productId, commercialId, date));
    }

    @PostMapping
    @Operation(operationId = "createPartnerCommission", summary = "Creer une commission configuree (reserve aux administrateurs)")
    public ResponseEntity<PartenaireProduitCommissionResponse> create(@Valid @RequestBody PartenaireProduitCommissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updatePartnerCommission", summary = "Mettre a jour une commission configuree (reserve aux administrateurs)")
    public ResponseEntity<PartenaireProduitCommissionResponse> update(@PathVariable Long id, @Valid @RequestBody PartenaireProduitCommissionRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deletePartnerCommission", summary = "Supprimer une commission configuree (reserve aux administrateurs)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
