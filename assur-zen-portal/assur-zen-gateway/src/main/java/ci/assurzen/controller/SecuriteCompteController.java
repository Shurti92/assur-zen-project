package ci.assurzen.controller;

import ci.assurzen.dto.MaConnexionResponse;
import ci.assurzen.dto.MaSessionResponse;
import ci.assurzen.services.SecuriteCompteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Parametres > Securite de l'utilisateur connecte (2026-10-03, refonte Espace Partenaire) : historique
 * de connexion, appareils connectes, signalement. Sous /auth/** (permitAll dans SecurityConfig) : le
 * service exige un utilisateur authentifie (401 sinon), aucun id d'utilisateur dans l'URL.
 */
@RestController
@RequestMapping("${env.ws}auth/me")
@Tag(name = "Securite du compte", description = "Historique de connexion et appareils connectes de l'utilisateur connecte")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SecuriteCompteController {

    private final SecuriteCompteService service;

    @GetMapping("/login-history")
    @Operation(operationId = "getMyLoginHistory",
            summary = "Mes dernieres connexions, reussies et echouees (size : 1 a 100, defaut 20)")
    public ResponseEntity<List<MaConnexionResponse>> historique(@RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.mesConnexions(size));
    }

    @PostMapping("/login-history/{id}/report")
    @Operation(operationId = "reportMyLogin",
            summary = "Signaler une connexion \"Ce n'etait pas moi\" (204, idempotent ; alerte les administrateurs)")
    public ResponseEntity<Void> signaler(@PathVariable Long id) {
        service.signalerConnexion(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    @Operation(operationId = "getMySessions", summary = "Mes appareils connectes (sessions actives), la session courante marquee")
    public ResponseEntity<List<MaSessionResponse>> sessions(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return ResponseEntity.ok(service.mesSessions(jeton(authorization)));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(operationId = "revokeMySession", summary = "Deconnecter un appareil (204 ; 404 si la session n'est pas active)")
    public ResponseEntity<Void> revoquer(@PathVariable String id) {
        service.revoquerSession(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    @Operation(operationId = "revokeMyOtherSessions",
            summary = "Deconnecter tous mes autres appareils (autres=true obligatoire ; la session courante est conservee)")
    public ResponseEntity<Map<String, Integer>> revoquerAutres(
            @RequestParam(defaultValue = "false") boolean autres,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!autres) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Parametre autres=true obligatoire");
        }
        return ResponseEntity.ok(Map.of("count", service.revoquerAutresSessions(jeton(authorization))));
    }

    private static String jeton(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
    }
}
