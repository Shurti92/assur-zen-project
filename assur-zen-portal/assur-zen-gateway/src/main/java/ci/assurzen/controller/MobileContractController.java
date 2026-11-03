package ci.assurzen.controller;

import ci.assurzen.dto.MobileContractDtos;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.MobileContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrats de l'assure connecte pour l'application mobile (lot b, demande mobile n°3, 2026-11-02). Reserve au role
 * CLIENT (SecurityConfig) ; chaque appel est limite aux contrats des assures lies au compte.
 */
@RestController
@RequestMapping("${env.ws}mobile/contracts")
@Tag(name = "Mobile - Contrats", description = "Lecture des contrats par l'assure connecte (application mobile)")
@RequiredArgsConstructor
public class MobileContractController {

    private final MobileContractService service;

    @GetMapping
    @Operation(operationId = "mobileListContracts", summary = "Mes contrats (ceux des assures lies a mon compte) ; filtre facultatif status = PENDING | ACTIVE | SUSPENDED | EXPIRED | TERMINATED")
    public ResponseEntity<MobileContractDtos.ContractList> lister(Authentication authentication,
                                                                 @RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.lister(utilisateur(authentication).userId(), status));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "mobileGetContract", summary = "Detail d'un de mes contrats (404 CONTRACT_NOT_FOUND pour un contrat d'un tiers) ; ETag et If-None-Match geres")
    public ResponseEntity<MobileContractDtos.ContractDetail> detail(Authentication authentication, @PathVariable Long id,
                                                                    @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        MobileContractDtos.ContractDetail detail = service.detail(utilisateur(authentication).userId(), id);
        String etag = "\"" + detail.id() + "-" + (detail.updatedAt() == null ? 0 : detail.updatedAt().toEpochMilli())
                + "-" + detail.status() + "-" + (detail.renewal() == null ? 0 : 1) + "-" + java.time.LocalDate.now() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(detail);
    }

    @PostMapping("/{id}/requests")
    @Operation(operationId = "mobileCreateContractRequest", summary = "Demander la modification ou la resiliation d'un de mes contrats actifs (201 {reference, expectedWithinHours} ; 404 contrat d'un tiers ; 409 CONTRACT_NOT_ACTIVE ; idempotent tant qu'une demande du meme type est en attente)")
    public ResponseEntity<ci.assurzen.dto.MobileContractRequestDtos.CreatedResponse> demander(
            Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody ci.assurzen.dto.MobileContractRequestDtos.CreateRequest request) {
        return ResponseEntity.status(201)
                .body(service.demander(utilisateur(authentication).userId(), id, request.type(), request.message()));
    }

    private AuthenticatedUserPrincipal utilisateur(Authentication authentication) {
        return (AuthenticatedUserPrincipal) authentication.getPrincipal();
    }
}
