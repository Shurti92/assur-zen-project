package ci.assurzen.controller;

import ci.assurzen.service.AuthService;
import ci.assurzen.webpush.dto.WebPushMessage;
import ci.assurzen.webpush.service.WebPushNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Push de test a la demande (demande de l'equipe EP, 2026-09-26) - UNIQUEMENT avec le profil dev/local :
 * le bean n'existe pas en recette ni en production, la route y repond 404. Envoie un message fixe,
 * sans donnee metier, aux seuls appareils de l'utilisateur connecte (jamais a un autre compte).
 */
@RestController
@Profile({"dev", "local"})
@RequestMapping("${env.ws}notifications/push")
@Tag(name = "Notifications", description = "Centre de notifications et abonnements Web Push de l'utilisateur connecte")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class WebPushTestController {

    private final WebPushNotificationService webPushNotificationService;
    private final AuthService authService;

    @PostMapping("/test")
    @Operation(operationId = "sendWebPushTest",
            summary = "[DEV uniquement] Envoyer une notification push de test a mes appareils abonnes - renvoie le nombre d'appareils atteints")
    public ResponseEntity<Map<String, Object>> envoyerTest() {
        int atteints = webPushNotificationService.sendToUser(authService.currentPrincipal().userId(), new WebPushMessage(
                "Notification de test",
                "Les notifications AssurZen fonctionnent sur cet appareil.",
                Map.of("type", "TEST", "reference", String.valueOf(System.currentTimeMillis()), "route", "/")));
        return ResponseEntity.ok(Map.of("pushActif", webPushNotificationService.estActif(), "appareilsAtteints", atteints));
    }
}
