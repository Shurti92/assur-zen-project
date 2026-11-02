package ci.assurzen.controller;

import ci.assurzen.dto.FundOutflowTypeRequest;
import ci.assurzen.dto.FundOutflowTypeResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.services.FundOutflowTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${env.ws}fund-outflow-types")
@Tag(name = "Sorties de fonds - Types", description = "Referentiel des types de sortie de fonds (LOAN, ADVANCE, EXPENSE...)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class FundOutflowTypeController {

    private final FundOutflowTypeService service;

    @GetMapping
    @Operation(operationId = "listFundOutflowTypes", summary = "Lister/rechercher les types de sortie de fonds (pagine, filtrable)")
    public ResponseEntity<PageResponse<FundOutflowTypeResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.search(q, actif, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getFundOutflowType", summary = "Detail d'un type de sortie de fonds")
    public ResponseEntity<FundOutflowTypeResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @Operation(operationId = "createFundOutflowType", summary = "Creer un type de sortie de fonds (reserve aux administrateurs)")
    public ResponseEntity<FundOutflowTypeResponse> create(@Valid @RequestBody FundOutflowTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateFundOutflowType", summary = "Mettre a jour un type de sortie de fonds (reserve aux administrateurs)")
    public ResponseEntity<FundOutflowTypeResponse> update(@PathVariable Long id, @Valid @RequestBody FundOutflowTypeRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteFundOutflowType", summary = "Supprimer un type de sortie de fonds non systeme (reserve aux administrateurs)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
