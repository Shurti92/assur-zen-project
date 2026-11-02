package ci.assurzen.webpush.config;

import ci.assurzen.entities.ConfigCategory;
import ci.assurzen.entities.ConfigValueType;
import ci.assurzen.entities.DynamicConfiguration;
import ci.assurzen.repositories.DynamicConfigurationRepository;
import ci.assurzen.services.ConfigurationCacheService;
import ci.assurzen.services.ConfigurationEncryptionService;
import ci.assurzen.services.DynamicConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Cles VAPID automatiques par environnement (2026-09-26) : au demarrage, si l'environnement ne fournit
 * pas VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY, la paire est lue en base (configuration dynamique) ou, au
 * tout premier demarrage, GENEREE puis enregistree : cle privee chiffree (ConfigurationEncryptionService,
 * comme les identifiants SycaPay), cle publique en clair. Chaque environnement ayant sa propre base,
 * chacun obtient sa propre paire sans aucune manipulation au deploiement ; le frontend lit la cle
 * publique via GET /notifications/push/public-key.
 *
 * Garde-fous :
 * - sans cle de chiffrement securisee (APP_CONFIG_ENCRYPTION_SECRET/SALT), RIEN n'est genere : jamais de
 *   cle privee en clair en base - push desactive, un log explique pourquoi ;
 * - la paire n'est JAMAIS regeneree si elle existe (la changer invaliderait tous les abonnements) ; une
 *   paire en base illisible desactive le push au lieu d'etre ecrasee ;
 * - les deux lignes sont non editables depuis le back-office.
 * Une base copiee d'un autre environnement emporte ses cles : definir alors VAPID_* dans l'environnement
 * (prioritaires), comme en local sur la copie de production.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VapidKeyInitializer {

    static final String CLE_PUBLIQUE = "WEBPUSH_VAPID_PUBLIC_KEY";
    static final String CLE_PRIVEE = "WEBPUSH_VAPID_PRIVATE_KEY";
    private static final String MODULE = "WEBPUSH";
    private static final String SYSTEME = "system";

    private final WebPushProperties properties;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final DynamicConfigurationRepository repository;
    private final ConfigurationEncryptionService encryptionService;
    private final ConfigurationCacheService cacheService;
    private final PlatformTransactionManager transactionManager;

    @EventListener(ApplicationReadyEvent.class)
    public void initialiser() {
        initialiser(true);
    }

    /** genererSiAbsente = false lors de la relecture apres une creation concurrente : jamais de boucle. */
    void initialiser(boolean genererSiAbsente) {
        if (properties.clesEnvironnementPresentes()) {
            return; // source prioritaire, deja chargee (ou refusee) par WebPushProperties.init()
        }
        if (!encryptionService.isCleSecurisee()) {
            log.warn("[WEBPUSH] ni cles VAPID_* dans l'environnement ni cle de chiffrement securisee "
                    + "(APP_CONFIG_ENCRYPTION_SECRET/SALT) pour les conserver en base - notifications push desactivees");
            return;
        }
        try {
            String publique = dynamicConfigurationService.getString(CLE_PUBLIQUE, null);
            String privee = dynamicConfigurationService.getString(CLE_PRIVEE, null);
            if (StringUtils.hasText(publique) && StringUtils.hasText(privee)) {
                properties.chargerCles(publique, privee, "base de donnees");
                return;
            }
            if (StringUtils.hasText(publique) || StringUtils.hasText(privee)) {
                log.error("[WEBPUSH] paire VAPID incomplete en base ({} / {}) - notifications push desactivees, "
                        + "a corriger manuellement (jamais regeneree automatiquement)", CLE_PUBLIQUE, CLE_PRIVEE);
                return;
            }
            if (genererSiAbsente) {
                genererEtEnregistrer();
            } else {
                log.error("[WEBPUSH] paire VAPID introuvable apres creation concurrente - notifications push desactivees");
            }
        } catch (RuntimeException e) {
            log.error("[WEBPUSH] cles VAPID en base illisibles ({}) - notifications push desactivees", e.getClass().getSimpleName());
        }
    }

    private void genererEtEnregistrer() {
        VapidKeyGenerator.Paire paire = VapidKeyGenerator.generer();
        String priveeChiffree = encryptionService.encrypt(paire.privee());
        try {
            // Les deux lignes ensemble ou aucune : jamais de paire incomplete en base.
            new TransactionTemplate(transactionManager).executeWithoutResult(statut -> {
                enregistrer(CLE_PUBLIQUE, paire.publique(), false,
                        "Cle publique VAPID du Web Push (applicationServerKey du frontend) - generee au premier demarrage.", 1);
                enregistrer(CLE_PRIVEE, priveeChiffree, true,
                        "Cle privee VAPID du Web Push, chiffree - ne jamais modifier (invaliderait tous les abonnements).", 2);
            });
        } catch (DataIntegrityViolationException e) {
            // Deux instances au premier demarrage : l'autre a enregistre sa paire en premier, on la relit.
            log.info("[WEBPUSH] paire VAPID deja creee par une autre instance - relecture");
            cacheService.evict(CLE_PUBLIQUE);
            cacheService.evict(CLE_PRIVEE);
            initialiser(false);
            return;
        }
        properties.chargerCles(paire.publique(), paire.privee(), "generee au premier demarrage");
        log.info("[WEBPUSH] paire VAPID generee et enregistree en base pour cet environnement");
    }

    private void enregistrer(String cle, String valeurStockee, boolean sensible, String description, int ordre) {
        DynamicConfiguration configuration = DynamicConfiguration.builder()
                .configKey(cle)
                .configValue(valeurStockee)
                .valueType(ConfigValueType.STRING)
                .description(description)
                .module(MODULE)
                .category(ConfigCategory.NOTIFICATION)
                .active(true)
                .editable(false)
                .sensitive(sensible)
                .restartRequired(true)
                .cacheEnabled(true)
                .displayOrder(ordre)
                .build();
        configuration.setCreatedBy(SYSTEME);
        configuration.setUpdatedBy(SYSTEME);
        DynamicConfiguration enregistree = repository.saveAndFlush(configuration);
        cacheService.put(cle, enregistree);
    }
}
