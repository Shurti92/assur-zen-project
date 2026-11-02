package ci.assurzen.config;

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
 * Reglages de l'Espace Partenaire (2026-10-04), crees au demarrage s'ils n'existent pas pour etre
 * REGLABLES DEPUIS LE BACK-OFFICE (module Configuration) dans chaque environnement, sans script SQL.
 * Une valeur deja presente n'est jamais ecrasee (meme principe que SycaPayBalanceReportConfigInitializer).
 * - Contact support affiche par l'Espace Partenaire (GET /me/support-contact) : VIDE par defaut, aucune
 *   coordonnee n'est inventee ;
 * - rappels automatiques (echeances, primes a reverser, documents) : DESACTIVES par defaut, ce sont de
 *   nouveaux envois aux partenaires.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EspacePartenaireConfigInitializer {

    private static final String SYSTEME = "system";

    private final DynamicConfigurationRepository repository;
    private final ConfigurationCacheService cacheService;

    @EventListener(ApplicationReadyEvent.class)
    public void initialiser() {
        creerSiAbsente("SUPPORT_NOM", "", ConfigValueType.STRING, "Contact support de l'Espace Partenaire : nom affiche (ex. Service partenaires AssurZen). Vide = carte masquee.", ConfigCategory.GENERAL, 10);
        creerSiAbsente("SUPPORT_FONCTION", "", ConfigValueType.STRING, "Contact support de l'Espace Partenaire : fonction ou service (facultatif).", ConfigCategory.GENERAL, 11);
        creerSiAbsente("SUPPORT_TELEPHONE", "", ConfigValueType.STRING, "Contact support de l'Espace Partenaire : telephone.", ConfigCategory.GENERAL, 12);
        creerSiAbsente("SUPPORT_EMAIL", "", ConfigValueType.STRING, "Contact support de l'Espace Partenaire : e-mail.", ConfigCategory.GENERAL, 13);
        creerSiAbsente("SUPPORT_WHATSAPP", "", ConfigValueType.STRING, "Contact support de l'Espace Partenaire : numero WhatsApp (facultatif).", ConfigCategory.GENERAL, 14);
        creerSiAbsente("SUPPORT_HORAIRES", "", ConfigValueType.STRING, "Contact support de l'Espace Partenaire : horaires (ex. Lun-Ven 8h-17h).", ConfigCategory.GENERAL, 15);
        creerSiAbsente("NOTIFICATIONS_RAPPELS_AUTO_ENABLED", "false", ConfigValueType.BOOLEAN,
                "Rappels automatiques de l'Espace Partenaire, chaque jour a 8h (in-app + push selon preferences, jamais de SMS) : echeance de contrat a J-30 et J-7, prime non reversee, document expirant ou expire.",
                ConfigCategory.NOTIFICATION, 20);
        creerSiAbsente("PRIME_NON_REVERSEE_RAPPEL_JOURS", "7", ConfigValueType.INTEGER, "MIN:1;MAX:365",
                "Rappel au partenaire d'une prime encaissee non reversee, ce nombre de jours apres la signature du contrat (une seule fois).",
                ConfigCategory.NOTIFICATION, 21);
    }

    private void creerSiAbsente(String cle, String valeur, ConfigValueType type, String description, ConfigCategory categorie, int ordre) {
        creerSiAbsente(cle, valeur, type, null, description, categorie, ordre);
    }

    private void creerSiAbsente(String cle, String valeur, ConfigValueType type, String regle, String description,
                                ConfigCategory categorie, int ordre) {
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
                .module("ESPACE_PARTENAIRE")
                .category(categorie)
                .editable(true)
                .displayOrder(ordre)
                .build();
        configuration.setCreatedBy(SYSTEME);
        configuration.setUpdatedBy(SYSTEME);
        try {
            cacheService.put(cle, repository.saveAndFlush(configuration));
            log.info("[ESPACE-PARTENAIRE] reglage {} cree, modifiable depuis le back-office", cle);
        } catch (DataIntegrityViolationException e) {
            log.info("[ESPACE-PARTENAIRE] reglage {} deja cree par une autre instance", cle);
        }
    }
}
