package ci.assurzen.controller;

import ci.assurzen.dto.MobileExtraDtos;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.MobileNotificationService;
import ci.assurzen.service.MobileProfileService;
import ci.assurzen.service.MobileSupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Profil, avis, notifications et support du client connecte (application mobile, 2026-11-03). Role CLIENT (/mobile/**). */
@RestController
@RequestMapping("${env.ws}mobile")
@Tag(name = "Mobile - Compte, notifications et support", description = "Profil, avis, notifications in-app et demandes d'aide du client connecte")
@RequiredArgsConstructor
public class MobileMeController {

    private final MobileProfileService profileService;
    private final MobileNotificationService notificationService;
    private final MobileSupportService supportService;

    @GetMapping("/me")
    @Operation(operationId = "mobileGetMe", summary = "Mon profil (id, nom, telephone, e-mail)")
    public ResponseEntity<MobileExtraDtos.ProfileView> moi(Authentication a) {
        return ResponseEntity.ok(profileService.lire(userId(a)));
    }

    @PatchMapping("/me")
    @Operation(operationId = "mobilePatchMe", summary = "Modifier mon nom et mon e-mail (le nom n'est pas lie a l'assure du back-office) ; 400 VALIDATION_FAILED avec field")
    public ResponseEntity<MobileExtraDtos.ProfileView> modifier(Authentication a, @Valid @RequestBody MobileExtraDtos.ProfilePatchRequest request) {
        return ResponseEntity.ok(profileService.modifier(userId(a), request));
    }

    @PostMapping("/feedback")
    @Operation(operationId = "mobileFeedback", summary = "Envoyer un avis (note de 1 a 5, commentaire et contrat facultatifs) ; 5 par jour au maximum")
    public ResponseEntity<MobileExtraDtos.FeedbackCreated> avis(Authentication a, @Valid @RequestBody MobileExtraDtos.FeedbackRequest request) {
        return ResponseEntity.status(201).body(profileService.avis(userId(a), request));
    }

    // ----- notifications

    @GetMapping("/notifications")
    @Operation(operationId = "mobileListNotifications", summary = "Mes notifications, du plus recent au plus ancien, avec le nombre de non lues (page, size : 50 max)")
    public ResponseEntity<MobileExtraDtos.NotificationPage> notifications(Authentication a, @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.lister(userId(a), page, size));
    }

    @GetMapping("/notifications/unread-count")
    @Operation(operationId = "mobileUnreadCount", summary = "Nombre de notifications non lues (point rouge de la cloche)")
    public ResponseEntity<MobileExtraDtos.UnreadCount> nonLues(Authentication a) {
        return ResponseEntity.ok(notificationService.nonLues(userId(a)));
    }

    @PostMapping("/notifications/{id}/read")
    @Operation(operationId = "mobileReadNotification", summary = "Marquer une notification comme lue (idempotent ; 404 NOTIFICATION_NOT_FOUND)")
    public ResponseEntity<MobileExtraDtos.NotificationItem> lire(Authentication a, @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.marquerLue(userId(a), id));
    }

    @PostMapping("/notifications/read-all")
    @Operation(operationId = "mobileReadAllNotifications", summary = "Marquer toutes mes notifications comme lues")
    public ResponseEntity<MobileExtraDtos.UnreadCount> toutLire(Authentication a) {
        return ResponseEntity.ok(notificationService.toutMarquerLu(userId(a)));
    }

    // ----- support

    @PostMapping("/support/requests")
    @Operation(operationId = "mobileCreateSupportRequest", summary = "Envoyer une demande d'aide (sujet issu de GET /public/support/topics, message de 5 a 2000 caracteres) ; 5 par heure (429 RATE_LIMITED)")
    public ResponseEntity<MobileExtraDtos.SupportCreated> demander(Authentication a, @Valid @RequestBody MobileExtraDtos.SupportCreateRequest request) {
        return ResponseEntity.status(201).body(supportService.creer(userId(a), request));
    }

    @GetMapping("/support/requests")
    @Operation(operationId = "mobileListSupportRequests", summary = "Mes demandes d'aide avec la reponse du conseiller")
    public ResponseEntity<MobileExtraDtos.SupportList> demandes(Authentication a) {
        return ResponseEntity.ok(supportService.lister(userId(a)));
    }

    @GetMapping("/support/requests/{id}")
    @Operation(operationId = "mobileGetSupportRequest", summary = "Detail d'une de mes demandes d'aide (404 REQUEST_NOT_FOUND)")
    public ResponseEntity<MobileExtraDtos.SupportItem> demande(Authentication a, @PathVariable Long id) {
        return ResponseEntity.ok(supportService.detail(userId(a), id));
    }

    private Long userId(Authentication a) {
        return ((AuthenticatedUserPrincipal) a.getPrincipal()).userId();
    }
}
