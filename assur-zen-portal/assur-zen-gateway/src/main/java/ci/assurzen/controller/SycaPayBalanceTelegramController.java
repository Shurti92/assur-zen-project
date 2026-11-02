package ci.assurzen.controller;

import ci.assurzen.sycapay.service.SycaPayBalanceReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Envoi immediat du solde SycaPay sur Telegram (2026-09-26) - en plus du rapport automatique de 8h.
 * Route sous /sycapay/** : reservee aux administrateurs (SecurityConfig).
 */
@RestController
@RequestMapping("${env.ws}sycapay/balance")
@Tag(name = "SycaPay", description = "Solde marchand, historique et reconciliation des virements Wave (paiement des commissions commerciales)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SycaPayBalanceTelegramController {

    private final SycaPayBalanceReportService reportService;

    @PostMapping("/telegram")
    @Operation(operationId = "sendSycaPayBalanceToTelegram",
            summary = "Envoyer maintenant le solde SycaPay sur Telegram (chat admin) - renvoie le texte envoye")
    public ResponseEntity<Map<String, String>> envoyer() {
        return ResponseEntity.ok(Map.of("message", reportService.envoyerRapport("Solde SycaPay - envoi a la demande")));
    }
}
