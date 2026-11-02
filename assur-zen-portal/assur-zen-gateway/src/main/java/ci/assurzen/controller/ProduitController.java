package ci.assurzen.controller;

import ci.assurzen.dto.ImportResult;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.ProduitRequest;
import ci.assurzen.dto.ProduitResponse;
import ci.assurzen.services.ProduitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("${env.ws}produits")
@Tag(name = "Produits", description = "CRUD des produits")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ProduitController {

    private final ProduitService service;

    @GetMapping
    @Operation(operationId = "listProduits", summary = "Lister/rechercher les produits (paginé) - filtrable par type de produit (ex. productTypeCode=FLOTTE)")
    public ResponseEntity<PageResponse<ProduitResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String productTypeCode,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.search(q, active, productTypeCode, page, size));
    }

    @GetMapping("/export")
    @Operation(operationId = "exportProduits", summary = "Exporter les produits au format CSV")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active) {
        byte[] csv = service.exportCsv(q, active);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("produits.csv").build().toString())
                .body(csv);
    }

    @PostMapping("/import")
    @Operation(operationId = "importProduits", summary = "Importer des produits en masse depuis un fichier CSV")
    public ResponseEntity<ImportResult> importCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.importCsv(file));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getProduit", summary = "Consulter un produit")
    public ResponseEntity<ProduitResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(operationId = "createProduit", summary = "Creer un produit")
    public ResponseEntity<ProduitResponse> create(@Valid @RequestBody ProduitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateProduit", summary = "Mettre a jour un produit")
    public ResponseEntity<ProduitResponse> update(@PathVariable Long id, @Valid @RequestBody ProduitRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteProduit", summary = "Supprimer un produit")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
