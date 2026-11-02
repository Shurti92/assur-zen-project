package ci.assurzen.controller;

import ci.assurzen.dto.ProduitCommissionCommercialRequest;
import ci.assurzen.dto.ProduitCommissionCommercialResponse;
import ci.assurzen.services.ProduitCommissionCommercialService;
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

/** Commission par contrat versee au commercial interne, configurable par produit (montant ou taux) -
 * mirroir de ProduitCommissionTauxController sans dimension type de partenaire. */
@RestController
@RequestMapping("${env.ws}produits-commission-commercial")
@Tag(name = "Commission commerciale", description = "Commission par contrat versee au commercial interne, configurable par produit (montant ou taux)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ProduitCommissionCommercialController {

    private final ProduitCommissionCommercialService service;

    @GetMapping
    @Operation(operationId = "listProduitCommissionCommercial", summary = "Lister/filtrer les commissions commerciales par produit")
    public ResponseEntity<List<ProduitCommissionCommercialResponse>> list(@RequestParam(required = false) Long produitId) {
        return ResponseEntity.ok(service.findAll(produitId));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getProduitCommissionCommercial", summary = "Consulter une commission commerciale")
    public ResponseEntity<ProduitCommissionCommercialResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(operationId = "createProduitCommissionCommercial", summary = "Creer la commission commerciale d'un produit (reserve aux administrateurs)")
    public ResponseEntity<ProduitCommissionCommercialResponse> create(@Valid @RequestBody ProduitCommissionCommercialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateProduitCommissionCommercial", summary = "Mettre a jour une commission commerciale (reserve aux administrateurs)")
    public ResponseEntity<ProduitCommissionCommercialResponse> update(@PathVariable Long id, @Valid @RequestBody ProduitCommissionCommercialRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteProduitCommissionCommercial", summary = "Supprimer une commission commerciale (reserve aux administrateurs)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
