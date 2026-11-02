package ci.assurzen.controller;

import ci.assurzen.dto.ProduitCommissionTauxRequest;
import ci.assurzen.dto.ProduitCommissionTauxResponse;
import ci.assurzen.entities.PartenaireType;
import ci.assurzen.services.ProduitCommissionTauxService;
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
@RequestMapping("${env.ws}produits-commission-taux")
@Tag(name = "Taux de commission", description = "Taux de commission par produit et type de partenaire (DIRECT/AGENT)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ProduitCommissionTauxController {

    private final ProduitCommissionTauxService service;

    @GetMapping
    @Operation(operationId = "listProduitCommissionTaux", summary = "Lister/filtrer les taux de commission (par produit, type de partenaire, ou directement par partenaire)")
    public ResponseEntity<List<ProduitCommissionTauxResponse>> list(
            @RequestParam(required = false) Long produitId,
            @RequestParam(required = false) PartenaireType typePartenaire,
            @RequestParam(required = false) Long partenaireId) {
        return ResponseEntity.ok(service.findAll(produitId, typePartenaire, partenaireId));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getProduitCommissionTaux", summary = "Consulter un taux de commission")
    public ResponseEntity<ProduitCommissionTauxResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(operationId = "createProduitCommissionTaux", summary = "Creer un taux de commission pour un produit et un type de partenaire")
    public ResponseEntity<ProduitCommissionTauxResponse> create(@Valid @RequestBody ProduitCommissionTauxRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateProduitCommissionTaux", summary = "Mettre a jour un taux de commission")
    public ResponseEntity<ProduitCommissionTauxResponse> update(@PathVariable Long id, @Valid @RequestBody ProduitCommissionTauxRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteProduitCommissionTaux", summary = "Supprimer un taux de commission")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
