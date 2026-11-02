package ci.assurzen.controller;

import ci.assurzen.dto.CommissionStatementResponse;
import ci.assurzen.services.CommissionStatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Releves mensuels des commissions versees a un partenaire (2026-10-03, refonte Espace Partenaire).
 * Lecture seule ; reserve au partenaire lui-meme et aux administrateurs (controle dans le service).
 */
@RestController
@RequestMapping("${env.ws}partenaires/{partenaireId}/commission-statements")
@Tag(name = "Releves de commissions", description = "Releves mensuels des commissions versees au partenaire")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CommissionStatementController {

    private final CommissionStatementService service;

    @GetMapping
    @Operation(operationId = "listCommissionStatements",
            summary = "Releves mensuels des commissions versees (mois ayant au moins un versement, du plus recent au plus ancien)")
    public ResponseEntity<List<CommissionStatementResponse>> lister(@PathVariable Long partenaireId) {
        return ResponseEntity.ok(service.lister(partenaireId));
    }

    @GetMapping("/{mois}")
    @Operation(operationId = "getCommissionStatement", summary = "Releve d'un mois (AAAA-MM) avec ses lignes (404 si aucun versement)")
    public ResponseEntity<CommissionStatementResponse> detail(@PathVariable Long partenaireId, @PathVariable String mois) {
        return ResponseEntity.ok(service.detail(partenaireId, mois));
    }

    @GetMapping(value = "/{mois}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(operationId = "downloadCommissionStatementPdf", summary = "Releve d'un mois au format PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable Long partenaireId, @PathVariable String mois) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"releve-commissions-" + mois + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.pdf(partenaireId, mois));
    }
}
