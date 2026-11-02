package ci.assurzen.controller;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.PartenaireNotificationResponse;
import ci.assurzen.services.PartenaireNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Notifications in-app de l'espace partenaire (2026-09-23) - commissions disponibles, gagnees ou
 * versees automatiquement. Accessible au partenaire concerne et aux administrateurs (controle au
 * niveau service : /partenaires/** reste ouvert a tout compte authentifie dans SecurityConfig, comme
 * les autres sous-ressources lues par l'espace partenaire).
 */
@RestController
@RequestMapping("${env.ws}partenaires/{partenaireId}/notifications")
@Tag(name = "Notifications partenaire", description = "Notifications in-app et historique des messages envoyes au partenaire")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PartenaireNotificationController {

    private final PartenaireNotificationService service;

    @GetMapping
    @Operation(operationId = "listPartenaireNotifications",
            summary = "Notifications du partenaire, les plus recentes d'abord (nonLuesSeulement=true pour les non lues)")
    public ResponseEntity<PageResponse<PartenaireNotificationResponse>> lister(
            @PathVariable Long partenaireId,
            @RequestParam(required = false, defaultValue = "false") boolean nonLuesSeulement,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.lister(partenaireId, nonLuesSeulement, page, size));
    }

    @GetMapping("/non-lues/count")
    @Operation(operationId = "countPartenaireNotificationsNonLues", summary = "Nombre de notifications non lues (badge)")
    public ResponseEntity<Map<String, Long>> compterNonLues(@PathVariable Long partenaireId) {
        return ResponseEntity.ok(Map.of("nonLues", service.compterNonLues(partenaireId)));
    }

    @PatchMapping("/{notificationId}/lu")
    @Operation(operationId = "markPartenaireNotificationLue", summary = "Marquer une notification comme lue")
    public ResponseEntity<PartenaireNotificationResponse> marquerLue(@PathVariable Long partenaireId,
                                                                     @PathVariable Long notificationId) {
        return ResponseEntity.ok(service.marquerLue(partenaireId, notificationId));
    }

    @PatchMapping("/lu")
    @Operation(operationId = "markAllPartenaireNotificationsLues", summary = "Marquer toutes les notifications comme lues")
    public ResponseEntity<Map<String, Integer>> marquerToutesLues(@PathVariable Long partenaireId) {
        return ResponseEntity.ok(Map.of("marquees", service.marquerToutesLues(partenaireId)));
    }
}
