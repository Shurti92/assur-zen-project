package ci.assurzen.controller;

import ci.assurzen.dto.LoginHistoryResponse;
import ci.assurzen.dto.LoginHistoryStatsResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.services.LoginHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Historique des connexions reussies (IP, appareil, geolocalisation approximee, date/heure), par
 * utilisateur et/ou par partenaire. Lecture seule : les entrees sont immuables, ecrites
 * uniquement par AuthService au moment du login. Reserve ADMIN (cf. SecurityConfig).
 */
@RestController
@RequestMapping("${env.ws}historique-connexions")
@Tag(name = "Historique de connexion", description = "Connexions reussies par utilisateur/partenaire : IP, appareil, geolocalisation approximee, frequence")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LoginHistoryController {

    private final LoginHistoryService loginHistoryService;

    @GetMapping
    @Operation(operationId = "listLoginHistory", summary = "Rechercher dans l'historique de connexion (pagine, filtrable par partenaire/utilisateur/periode) (reserve aux administrateurs)")
    public ResponseEntity<PageResponse<LoginHistoryResponse>> search(
            @RequestParam(required = false) Long partenaireId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFin,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(loginHistoryService.search(partenaireId, userId, dateDebut, dateFin, page, size));
    }

    @GetMapping("/statistiques")
    @Operation(operationId = "getLoginHistoryStatistics", summary = "Nombre de connexions aujourd'hui / cette semaine / ce mois, pour un partenaire, un utilisateur, ou globalement (reserve aux administrateurs)")
    public ResponseEntity<LoginHistoryStatsResponse> statistiques(
            @RequestParam(required = false) Long partenaireId,
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(loginHistoryService.statistiques(partenaireId, userId));
    }
}
