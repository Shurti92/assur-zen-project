package ci.assurzen.sycapay.service;

import ci.assurzen.events.ConfigurationActivatedEvent;
import ci.assurzen.events.ConfigurationChangedEvent;
import ci.assurzen.events.ConfigurationDeactivatedEvent;
import ci.assurzen.repositories.DynamicConfigurationRepository;
import ci.assurzen.services.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Identifiants SycaPay modifies en base (2026-09-24) : pris en compte IMMEDIATEMENT, sans redemarrage -
 * le jeton SycaPay et le solde en cache, obtenus avec les anciens identifiants, sont oublies. Chaque
 * changement declenche aussi une alerte de securite Telegram (cle modifiee + auteur, JAMAIS la valeur) :
 * un changement non attendu des identifiants de paiement doit etre visible immediatement.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SycaPayIdentifiantsListener {

    static final Set<String> CLES_IDENTIFIANTS = Set.of("SYCAPAY_MERCHANT_ID", "SYCAPAY_API_KEY", "SYCAPAY_SECRET_KEY");

    private final SycaPayAuthenticationService authenticationService;
    private final SycaPayBalanceService balanceService;
    private final DynamicConfigurationRepository configurationRepository;
    private final TelegramNotificationService telegramNotificationService;

    @EventListener
    public void onChanged(ConfigurationChangedEvent event) {
        traiter(event.configKey(), event.newValue() == null ? "supprime" : "modifie");
    }

    @EventListener
    public void onActivated(ConfigurationActivatedEvent event) {
        traiter(event.configKey(), "active");
    }

    @EventListener
    public void onDeactivated(ConfigurationDeactivatedEvent event) {
        traiter(event.configKey(), "desactive");
    }

    private void traiter(String configKey, String action) {
        if (configKey == null || !CLES_IDENTIFIANTS.contains(configKey)) {
            return;
        }
        authenticationService.invalidate();
        balanceService.viderCache();

        String auteur = configurationRepository.findByConfigKey(configKey)
                .map(c -> c.getUpdatedBy())
                .orElse("inconnu");
        log.warn("[SYCAPAY] identifiant {} {} par {} - jeton et solde en cache invalides", configKey, action, auteur);
        telegramNotificationService.envoyer("<b>Securite - identifiant SycaPay " + action + "</b>\n"
                + "Cle : " + configKey + "\n"
                + "Par : " + TelegramNotificationService.echapperHtml(auteur) + "\n"
                + "Si ce changement n'est pas attendu, verifiez immediatement le module Configuration.");
    }
}
