package ci.assurzen.controller;

import ci.assurzen.dto.AuditFluxResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.services.AuditFluxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${env.ws}audit")
@Tag(name = "Audit", description = "Consultation du journal d'audit technique (t_assur_zen_audit_flux)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AuditController {

    private final AuditFluxService auditFluxService;

    @GetMapping
    @Operation(operationId = "listAuditFlux", summary = "Lister le journal d'audit (paginé, tri par date décroissante)")
    public ResponseEntity<PageResponse<AuditFluxResponse>> list(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(auditFluxService.list(page, size));
    }
}
