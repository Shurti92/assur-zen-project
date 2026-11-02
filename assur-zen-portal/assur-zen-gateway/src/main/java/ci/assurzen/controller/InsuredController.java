package ci.assurzen.controller;

import ci.assurzen.dto.ImportResult;
import ci.assurzen.dto.InsuredFicheResponse;
import ci.assurzen.dto.InsuredRequest;
import ci.assurzen.dto.InsuredResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.TypeClient;
import ci.assurzen.services.InsuredFicheService;
import ci.assurzen.services.InsuredService;
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
@RequestMapping("${env.ws}insureds")
@Tag(name = "Assures", description = "CRUD des assures")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class InsuredController {

    private final InsuredService service;
    private final InsuredFicheService ficheService;

    @GetMapping
    @Operation(operationId = "listInsureds", summary = "Lister/rechercher les assures (paginé)")
    public ResponseEntity<PageResponse<InsuredResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TypeClient typeClient,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.search(q, typeClient, page, size));
    }

    @GetMapping("/export")
    @Operation(operationId = "exportInsureds", summary = "Exporter les assures au format CSV")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q, @RequestParam(required = false) TypeClient typeClient) {
        byte[] csv = service.exportCsv(q, typeClient);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("assures.csv").build().toString())
                .body(csv);
    }

    @PostMapping("/import")
    @Operation(operationId = "importInsureds", summary = "Importer des assures en masse depuis un fichier CSV")
    public ResponseEntity<ImportResult> importCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.importCsv(file));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getInsured", summary = "Consulter un assure")
    public ResponseEntity<InsuredResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/{id}/fiche-sinistralite")
    @Operation(operationId = "getInsuredFicheSinistralite",
            summary = "Fiche de sinistralite d'un assure : historique complet des contrats depuis le debut et sinistres associes, filtrable par exercice comptable")
    public ResponseEntity<InsuredFicheResponse> getFicheSinistralite(
            @PathVariable Long id,
            @RequestParam(required = false) Long exerciceComptableId) {
        return ResponseEntity.ok(ficheService.getFiche(id, exerciceComptableId));
    }

    @PostMapping
    @Operation(operationId = "createInsured", summary = "Creer un assure")
    public ResponseEntity<InsuredResponse> create(@Valid @RequestBody InsuredRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateInsured", summary = "Mettre a jour un assure")
    public ResponseEntity<InsuredResponse> update(@PathVariable Long id, @Valid @RequestBody InsuredRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteInsured", summary = "Supprimer un assure")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
