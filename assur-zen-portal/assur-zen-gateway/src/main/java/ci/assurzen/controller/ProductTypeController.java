package ci.assurzen.controller;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.ProductTypeRequest;
import ci.assurzen.dto.ProductTypeResponse;
import ci.assurzen.dto.ProductTypeSummaryResponse;
import ci.assurzen.dto.ProduitResponse;
import ci.assurzen.services.ProductTypeService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}product-types")
@Tag(name = "Types de produit", description = "Referentiel des types de produit (AUTO, MOTO, VIE...) auquel chaque produit appartient")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ProductTypeController {

    private final ProductTypeService productTypeService;

    @GetMapping
    @Operation(operationId = "listProductTypes", summary = "Lister/rechercher les types de produit (paginé, filtrable)")
    public ResponseEntity<PageResponse<ProductTypeResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(productTypeService.search(q, actif, page, size));
    }

    @GetMapping("/active")
    @Operation(operationId = "listActiveProductTypes", summary = "Liste legere des types actifs (listes deroulantes)")
    public ResponseEntity<List<ProductTypeSummaryResponse>> listActive() {
        return ResponseEntity.ok(productTypeService.listActive());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getProductType", summary = "Detail d'un type de produit")
    public ResponseEntity<ProductTypeResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(productTypeService.get(id));
    }

    @GetMapping("/{id}/products")
    @Operation(operationId = "getProductTypeProducts", summary = "Lister les produits rattaches a ce type")
    public ResponseEntity<List<ProduitResponse>> produits(@PathVariable Long id) {
        return ResponseEntity.ok(productTypeService.produits(id));
    }

    @PostMapping
    @Operation(operationId = "createProductType", summary = "Creer un type de produit (reserve aux administrateurs/gestionnaires de produits)")
    public ResponseEntity<ProductTypeResponse> create(@Valid @RequestBody ProductTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productTypeService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateProductType", summary = "Mettre a jour un type de produit (reserve aux administrateurs/gestionnaires de produits)")
    public ResponseEntity<ProductTypeResponse> update(@PathVariable Long id, @Valid @RequestBody ProductTypeRequest request) {
        return ResponseEntity.ok(productTypeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteProductType", summary = "Supprimer un type de produit non utilise (reserve aux administrateurs/gestionnaires de produits)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/enable")
    @Operation(operationId = "enableProductType", summary = "Reactiver un type de produit (reserve aux administrateurs/gestionnaires de produits)")
    public ResponseEntity<ProductTypeResponse> enable(@PathVariable Long id) {
        return ResponseEntity.ok(productTypeService.enable(id));
    }

    @PatchMapping("/{id}/disable")
    @Operation(operationId = "disableProductType", summary = "Desactiver un type de produit sans produit actif rattache (reserve aux administrateurs/gestionnaires de produits)")
    public ResponseEntity<ProductTypeResponse> disable(@PathVariable Long id) {
        return ResponseEntity.ok(productTypeService.disable(id));
    }
}
