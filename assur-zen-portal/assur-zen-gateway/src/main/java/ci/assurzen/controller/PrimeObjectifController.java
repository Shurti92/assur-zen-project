package ci.assurzen.controller;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.PrimeObjectifRequest;
import ci.assurzen.dto.PrimeObjectifResponse;
import ci.assurzen.dto.PrimeObjectifStatutRequest;
import ci.assurzen.entities.StatutPrimeObjectif;
import ci.assurzen.services.PrimeObjectifService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${env.ws}primes-objectif")
@Tag(name = "Primes objectif", description = "Prime versee a un partenaire ayant atteint/depasse son objectif de production annuel (reserve aux administrateurs)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PrimeObjectifController {

    private final PrimeObjectifService service;

    @GetMapping
    @Operation(operationId = "listPrimesObjectif", summary = "Lister/filtrer les demandes de prime d'objectif (pagine)")
    public ResponseEntity<PageResponse<PrimeObjectifResponse>> list(
            @RequestParam(required = false) Long partenaireId,
            @RequestParam(required = false) StatutPrimeObjectif statut,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.list(partenaireId, statut, page, size));
    }

    @PostMapping
    @Operation(operationId = "demanderPrimeObjectif", summary = "Creer une demande de prime d'objectif pour un partenaire (refuse en 409 si non eligible)")
    public ResponseEntity<PrimeObjectifResponse> demander(@Valid @RequestBody PrimeObjectifRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.demander(request));
    }

    @PatchMapping("/{id}/statut")
    @Operation(operationId = "changerStatutPrimeObjectif", summary = "Valider/rejeter/marquer payee une demande de prime d'objectif")
    public ResponseEntity<PrimeObjectifResponse> changerStatut(@PathVariable Long id, @Valid @RequestBody PrimeObjectifStatutRequest request) {
        return ResponseEntity.ok(service.changerStatut(id, request.statut(), request.motifRejet()));
    }
}
