package ci.assurzen.controller;

import ci.assurzen.dto.MobileClaimDtos;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.MobileClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Sinistres de l'assure connecte pour l'application mobile (demande n°6, 2026-11-02). Reserve au role CLIENT
 * (SecurityConfig, /mobile/**) ; chaque appel est limite aux sinistres des assures lies au compte.
 */
@RestController
@RequestMapping("${env.ws}mobile")
@Tag(name = "Mobile - Sinistres", description = "Declaration, pieces et suivi des sinistres (application mobile)")
@RequiredArgsConstructor
public class MobileClaimController {

    private final MobileClaimService service;

    @GetMapping("/products/{productId}/claim-types")
    @Operation(operationId = "mobileClaimTypes", summary = "Types de sinistre proposes pour un produit (productId = identifiant lisible : auto, moto, sante...)")
    public ResponseEntity<MobileClaimDtos.ClaimTypeList> types(@PathVariable String productId) {
        return ResponseEntity.ok(service.types(productId));
    }

    @PostMapping("/claims")
    @Operation(operationId = "mobileDeclareClaim", summary = "Declarer un sinistre sur un de mes contrats actifs (409 CLAIM_NOT_ALLOWED sinon ; 400 OCCURRED_AT_OUT_OF_RANGE) ; envoie le SMS de declaration a l'assure")
    public ResponseEntity<MobileClaimDtos.Created> declarer(Authentication authentication, @Valid @RequestBody MobileClaimDtos.CreateRequest request) {
        return ResponseEntity.status(201).body(service.declarer(utilisateur(authentication).userId(), request));
    }

    @GetMapping("/claims")
    @Operation(operationId = "mobileListClaims", summary = "Mes sinistres, du plus recent au plus ancien, avec etapes de suivi et historique")
    public ResponseEntity<MobileClaimDtos.ClaimList> lister(Authentication authentication) {
        return ResponseEntity.ok(service.lister(utilisateur(authentication).userId()));
    }

    @GetMapping("/claims/{id}")
    @Operation(operationId = "mobileGetClaim", summary = "Detail et suivi d'un de mes sinistres (404 CLAIM_NOT_FOUND pour le sinistre d'un tiers)")
    public ResponseEntity<MobileClaimDtos.Claim> detail(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(service.detail(utilisateur(authentication).userId(), id));
    }

    @PostMapping(value = "/claims/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "mobileAddClaimFile", summary = "Ajouter une piece (kind = PHOTO ou DOCUMENT ; JPEG/PNG, PDF pour les documents ; 6 photos et 3 documents au maximum ; taille max MAX_FILE_SIZE)")
    public ResponseEntity<MobileClaimDtos.FileRef> ajouterFichier(Authentication authentication, @PathVariable Long id,
                                                                  @RequestParam("kind") String kind,
                                                                  @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(201).body(service.ajouterFichier(utilisateur(authentication).userId(), id, kind, file));
    }

    @GetMapping("/claims/{id}/files")
    @Operation(operationId = "mobileListClaimFiles", summary = "Pieces deja envoyees pour un de mes sinistres, avec les maximums autorises")
    public ResponseEntity<MobileClaimDtos.FileList> fichiers(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(service.fichiers(utilisateur(authentication).userId(), id));
    }

    private AuthenticatedUserPrincipal utilisateur(Authentication authentication) {
        return (AuthenticatedUserPrincipal) authentication.getPrincipal();
    }
}
