package ci.assurzen.controller;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.VignetteCommissionPaymentRequest;
import ci.assurzen.dto.VignetteCommissionResponse;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.services.VignetteCommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Registre des commissions forfaitaires dues a l'apporteur (partenaire ou commercial interne) d'une
 * demande de vignette moto - contrat "commission apporteur vignette", 2026-09-15. Les lignes sont
 * creees automatiquement par POST /vignettes-moto/demandes des qu'un apporteur est renseigne ;
 * aucune creation manuelle possible ici, seulement consultation et paiement.
 */
@RestController
@RequestMapping("${env.ws}vignettes-moto/commissions")
@Tag(name = "Vignettes Moto - Commissions", description = "Commission forfaitaire due a l'apporteur (partenaire ou commercial) d'une demande de vignette moto")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class VignetteCommissionController {

    private final VignetteCommissionService service;

    @GetMapping
    @Operation(operationId = "listVignetteCommissions", summary = "Lister/filtrer les commissions vignette (reserve aux administrateurs)")
    public ResponseEntity<PageResponse<VignetteCommissionResponse>> list(
            @RequestParam(required = false) CommissionStatus statut,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.list(statut, page, size));
    }

    @GetMapping("/partenaires/{partenaireId}")
    @Operation(operationId = "listVignetteCommissionsByPartenaire", summary = "Lister les commissions vignette d'un partenaire (reserve aux administrateurs)")
    public ResponseEntity<PageResponse<VignetteCommissionResponse>> listByPartenaire(
            @PathVariable Long partenaireId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.listByPartenaire(partenaireId, page, size));
    }

    @GetMapping("/commerciaux/{commercialId}")
    @Operation(operationId = "listVignetteCommissionsByCommercial", summary = "Lister les commissions vignette d'un commercial interne (reserve aux administrateurs)")
    public ResponseEntity<PageResponse<VignetteCommissionResponse>> listByCommercial(
            @PathVariable Long commercialId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.listByCommercial(commercialId, page, size));
    }

    @PostMapping("/payer")
    @Operation(operationId = "payerVignetteCommissions", summary = "Payer une ou plusieurs commissions vignette (SMS agrege par apporteur, reserve aux administrateurs)")
    public ResponseEntity<List<VignetteCommissionResponse>> payer(@Valid @RequestBody VignetteCommissionPaymentRequest request) {
        return ResponseEntity.ok(service.payer(request));
    }
}
