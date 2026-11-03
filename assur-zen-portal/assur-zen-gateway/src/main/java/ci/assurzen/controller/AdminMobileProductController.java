package ci.assurzen.controller;

import ci.assurzen.dto.AdminMobileProductDtos;
import ci.assurzen.service.MobileProductContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Parametrage du catalogue mobile depuis le back-office (2026-11-03) : contenu, visibilite et ordre d'affichage de chaque
 * produit. Reserve au role ADMIN (SecurityConfig /admin/mobile/** et verification dans le service).
 */
@RestController
@RequestMapping("${env.ws}admin/mobile/products")
@Tag(name = "Administration mobile - Catalogue", description = "Contenu des produits affiche dans l'application mobile (reserve aux administrateurs)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AdminMobileProductController {

    private final MobileProductContentService service;

    @GetMapping
    @Operation(operationId = "adminMobileListProducts", summary = "Tous les produits avec leur contenu mobile (configured=false : aucun contenu, produit invisible sur le mobile)")
    public ResponseEntity<AdminMobileProductDtos.ProductContentList> lister() {
        return ResponseEntity.ok(service.lister());
    }

    @GetMapping("/{productId}")
    @Operation(operationId = "adminMobileGetProduct", summary = "Contenu mobile d'un produit")
    public ResponseEntity<AdminMobileProductDtos.ProductContent> lire(@PathVariable Long productId) {
        return ResponseEntity.ok(service.lire(productId));
    }

    @PutMapping("/{productId}")
    @Operation(operationId = "adminMobileSaveProduct", summary = "Parametrer le contenu mobile d'un produit : visibilite, nom court, famille, description, avantages, fonctionnement, eligibilite, garanties, icone, ordre, souscription en ligne. Remplace tout le contenu (champs absents effaces)")
    public ResponseEntity<AdminMobileProductDtos.ProductContent> enregistrer(@PathVariable Long productId,
                                                                            @Valid @RequestBody AdminMobileProductDtos.ContentRequest request) {
        return ResponseEntity.ok(service.enregistrer(productId, request));
    }

    @PutMapping("/order")
    @Operation(operationId = "adminMobileOrderProducts", summary = "Fixer l'ordre d'affichage : liste des identifiants de produits dans l'ordre voulu")
    public ResponseEntity<AdminMobileProductDtos.ProductContentList> ordonner(@Valid @RequestBody AdminMobileProductDtos.OrderRequest request) {
        return ResponseEntity.ok(service.ordonner(request.productIds()));
    }
}
