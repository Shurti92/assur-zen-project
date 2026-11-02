package ci.assurzen.controller;

import ci.assurzen.dto.NotificationPreferenceDto;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.PartenaireNotificationResponse;
import ci.assurzen.services.NotificationPreferenceService;
import ci.assurzen.services.PartenaireNotificationService;
import ci.assurzen.webpush.dto.PushPublicKeyResponse;
import ci.assurzen.webpush.dto.PushSubscriptionRequest;
import ci.assurzen.webpush.dto.PushSubscriptionResponse;
import ci.assurzen.webpush.dto.PushUnsubscribeRequest;
import ci.assurzen.webpush.service.WebPushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Notifications de l'utilisateur CONNECTE (2026-09-26) - aucun id dans l'URL, tout est deduit du JWT :
 * centre de notifications (partenaire, ou commercial interne depuis le 2026-10-03) et abonnements Web Push
 * de l'application EP (contrat notifications-push.md). Aucun endpoint ne permet d'envoyer une
 * notification : l'envoi est exclusivement declenche par les evenements metier cote serveur.
 */
@RestController
@RequestMapping("${env.ws}notifications")
@Tag(name = "Notifications", description = "Centre de notifications et abonnements Web Push de l'utilisateur connecte")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class NotificationController {

    private final PartenaireNotificationService notificationService;
    private final WebPushSubscriptionService subscriptionService;
    private final NotificationPreferenceService preferenceService;

    @GetMapping
    @Operation(operationId = "listMyNotifications",
            summary = "Mes notifications (partenaire ou commercial interne), les plus recentes d'abord, sans celles que j'ai supprimees (nonLuesSeulement=true pour les non lues)")
    public ResponseEntity<PageResponse<PartenaireNotificationResponse>> lister(
            @RequestParam(required = false, defaultValue = "false") boolean nonLuesSeulement,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(notificationService.listerMesNotifications(nonLuesSeulement, page, size));
    }

    @GetMapping("/unread-count")
    @Operation(operationId = "countMyUnreadNotifications", summary = "Nombre de mes notifications non lues (badge)")
    public ResponseEntity<Map<String, Long>> compterNonLues() {
        return ResponseEntity.ok(Map.of("count", notificationService.compterMesNonLues()));
    }

    @PutMapping("/{id}/read")
    @Operation(operationId = "markMyNotificationRead", summary = "Marquer une de mes notifications comme lue (404 si elle ne m'appartient pas)")
    public ResponseEntity<PartenaireNotificationResponse> marquerLue(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.marquerMaNotificationLue(id));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteMyNotification",
            summary = "Supprimer une de mes notifications de mon centre de notifications (204 ; 404 si elle ne m'appartient pas ou est deja supprimee)")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        notificationService.supprimerMaNotification(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    @Operation(operationId = "markAllMyNotificationsRead", summary = "Marquer toutes mes notifications comme lues")
    public ResponseEntity<Map<String, Integer>> marquerToutesLues() {
        return ResponseEntity.ok(Map.of("count", notificationService.marquerToutesMesNotificationsLues()));
    }

    @GetMapping("/preferences")
    @Operation(operationId = "getMyNotificationPreferences",
            summary = "Mes preferences de notification (push/SMS) par categorie - seulement les categories que je peux recevoir")
    public ResponseEntity<List<NotificationPreferenceDto>> preferences() {
        return ResponseEntity.ok(preferenceService.mesPreferences());
    }

    @PutMapping("/preferences")
    @Operation(operationId = "updateMyNotificationPreferences",
            summary = "Modifier mes preferences (categories fournies seulement ; un SMS non modifiable reste active ; 400 pour une categorie indisponible)")
    public ResponseEntity<List<NotificationPreferenceDto>> modifierPreferences(@RequestBody List<NotificationPreferenceDto> preferences) {
        return ResponseEntity.ok(preferenceService.modifierMesPreferences(preferences));
    }

    @PostMapping("/push-subscriptions")
    @Operation(operationId = "subscribeWebPush",
            summary = "Enregistrer (upsert par endpoint) l'abonnement Web Push de ce navigateur pour l'utilisateur connecte")
    public ResponseEntity<PushSubscriptionResponse> abonner(
            @Valid @RequestBody PushSubscriptionRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.enregistrer(request, userAgent));
    }

    @DeleteMapping("/push-subscriptions")
    @Operation(operationId = "unsubscribeWebPush",
            summary = "Supprimer l'abonnement Web Push de ce navigateur s'il appartient a l'utilisateur connecte (204 meme si inconnu)")
    public ResponseEntity<Void> desabonner(@Valid @RequestBody PushUnsubscribeRequest request) {
        subscriptionService.supprimer(request.endpoint());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/push/public-key")
    @Operation(operationId = "getWebPushPublicKey",
            summary = "Cle publique VAPID (applicationServerKey) et disponibilite du push dans cet environnement")
    public ResponseEntity<PushPublicKeyResponse> clePublique() {
        return ResponseEntity.ok(subscriptionService.clePublique());
    }
}
