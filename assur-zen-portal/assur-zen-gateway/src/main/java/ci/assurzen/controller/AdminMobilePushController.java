package ci.assurzen.controller;

import ci.assurzen.dto.MobileExtraDtos;
import ci.assurzen.service.FcmPushService;
import ci.assurzen.services.CommercialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Etat de la configuration des notifications push (Firebase), reserve aux administrateurs (2026-11-03). */
@RestController
@RequestMapping("${env.ws}admin/mobile/push")
@Tag(name = "Administration mobile - Push", description = "Etat de la configuration Firebase Cloud Messaging (reserve aux administrateurs)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AdminMobilePushController {

    private final FcmPushService pushService;
    private final CommercialService commercialService;

    @GetMapping("/status")
    @Operation(operationId = "adminMobilePushStatus", summary = "Verifie le compte de service Firebase (fichier lu, authentification Google, projet) sans envoyer de message ; indique si l'envoi est actif")
    public ResponseEntity<MobileExtraDtos.PushStatus> statut() {
        commercialService.requireAdmin();
        return ResponseEntity.ok(pushService.verifier());
    }
}
