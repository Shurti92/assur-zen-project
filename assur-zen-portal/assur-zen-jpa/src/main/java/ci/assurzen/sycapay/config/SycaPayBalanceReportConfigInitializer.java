package ci.assurzen.sycapay.config;

import ci.assurzen.entities.ConfigCategory;
import ci.assurzen.entities.ConfigValueType;
import ci.assurzen.entities.DynamicConfiguration;
import ci.assurzen.repositories.DynamicConfigurationRepository;
import ci.assurzen.services.ConfigurationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Reglages du rapport de solde SycaPay sur Telegram (2026-09-26), crees au demarrage s'ils n'existent pas
 * pour etre REGLABLES DEPUIS LE BACK-OFFICE (module Configuration) sans script SQL, dans chaque
 * environnement. Une valeur deja presente (modifiee par un administrateur) n'est jamais ecrasee.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SycaPayBalanceReportConfigInitializer {

    private static final String SYSTEME = "system";

    private final DynamicConfigurationRepository repository;
    private final ConfigurationCacheService cacheService;

    @EventListener(ApplicationReadyEvent.class)
    public void initialiser() {
        creerSiAbsente("SYCAPAY_BALANCE_ALERT_THRESHOLD", "50000", ConfigValueType.LONG, "MIN:0;MAX:100000000",
                "Seuil d'alerte du solde Wave SycaPay (FCFA) : en dessous, alerte Telegram (au plus une par jour, verifiee de 9h a 19h).",
                150);
        creerSiAbsente("SYCAPAY_BALANCE_REPORT_ENABLED", "true", ConfigValueType.BOOLEAN, null,
                "Envoie sur Telegram le solde SycaPay chaque jour ouvre a 8h et l'alerte de seuil bas.", 160);
    }

    private void creerSiAbsente(String cle, String valeur, ConfigValueType type, String regle, String description, int ordre) {
        if (repository.findByConfigKey(cle).isPresent()) {
            return;
        }
        DynamicConfiguration configuration = DynamicConfiguration.builder()
                .configKey(cle)
                .configValue(valeur)
                .defaultValue(valeur)
                .valueType(type)
                .validationRule(regle)
                .description(description)
                .module("SYCAPAY")
                .category(ConfigCategory.PAYMENT)
                .editable(true)
                .displayOrder(ordre)
                .build();
        configuration.setCreatedBy(SYSTEME);
        configuration.setUpdatedBy(SYSTEME);
        try {
            cacheService.put(cle, repository.saveAndFlush(configuration));
            log.info("[SYCAPAY] reglage {} cree ({}), modifiable depuis le back-office", cle, valeur);
        } catch (DataIntegrityViolationException e) {
            log.info("[SYCAPAY] reglage {} deja cree par une autre instance", cle);
        }
    }
}
