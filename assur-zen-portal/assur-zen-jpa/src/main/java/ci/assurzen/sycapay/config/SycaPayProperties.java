package ci.assurzen.sycapay.config;

import ci.assurzen.services.ConfigurationEncryptionService;
import ci.assurzen.services.DynamicConfigurationService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * merchantId/apiKey/secretKey (decision revue le 2026-09-24, demande explicite : modifiables sans
 * redemarrage) : lus EN PRIORITE en base, CHIFFRES AES-256-GCM (cles SYCAPAY_MERCHANT_ID /
 * SYCAPAY_API_KEY / SYCAPAY_SECRET_KEY, toujours sensibles - cf. DynamicConfigurationService), et
 * UNIQUEMENT si la cle de chiffrement est securisee (APP_CONFIG_ENCRYPTION_SECRET/SALT fournis par
 * l'environnement). Sinon, ou si la ligne est absente/vide/illisible : repli sur les variables
 * d'environnement du meme nom (comportement historique). Jamais de valeur dans un log.
 * Tous les autres reglages (operationnels, non sensibles) sont modifiables a chaud depuis le
 * back-office via le Dynamic Configuration Engine deja existant dans ce projet (module "SYCAPAY",
 * cle prefixee SYCAPAY_) : chaque getter concerne consulte dynamicConfigurationService en priorite et
 * ne retombe sur la valeur YAML/env (ce champ) que si aucune ligne n'existe encore en base pour cette
 * cle - jamais d'erreur si le seed n'a pas encore ete applique.
 *
 * dynamicConfigurationService est injecte par CHAMP (@Autowired(required = false), pas par
 * constructeur) pour deux raisons : (1) permettre a `new SycaPayProperties()` de continuer a
 * fonctionner tel quel dans les tests unitaires existants (le champ reste simplement null, les
 * getters degradent alors silencieusement vers la valeur statique) ; (2) meme motif que le champ
 * @Lazy AuthService de DynamicConfigurationService lui-meme - eviter de contraindre l'ordre
 * d'initialisation d'un bean de configuration cree tot dans le cycle Spring.
 *
 * connectTimeoutMs/readTimeoutMs restent lus dynamiquement ici, mais SycaPayClientImpl les foge dans
 * son OkHttpClient UNE SEULE FOIS a la construction du bean (cf. sa Javadoc) : un changement de ces
 * deux cles precis ne prend reellement effet qu'apres redemarrage - a signaler cote back-office via
 * restart_required=true sur ces deux lignes du seed SQL.
 */
@Configuration
@ConfigurationProperties(prefix = "sycapay")
@Getter
@Setter
@Slf4j
public class SycaPayProperties {

    private static final String MODULE_PREFIX = "SYCAPAY_";

    @Autowired(required = false)
    private DynamicConfigurationService dynamicConfigurationService;

    @Autowired(required = false)
    private ConfigurationEncryptionService encryptionService;

    private boolean enabled = false;

    private String baseUrl = "https://www.sycaretail.com/delivery/v1";

    private String merchantId;

    private String apiKey;

    private String secretKey;

    /** Perimetre integration : Wave uniquement par defaut (decision produit) - modifiable depuis le
     * back-office si le perimetre evolue, jamais deduit automatiquement du beneficiaire. */
    private String provider = "Wave";

    private String currency = "XOF";

    private String language = "fr";

    private long connectTimeoutMs = 5000L;

    private long readTimeoutMs = 15000L;

    /**
     * Duree de vie du token, confirmee a 40 secondes (2026-09-22) - le PDF source annoncait ce chiffre
     * de facon contradictoire selon la page (60s p.5, 40s p.6), leve par confirmation directe.
     */
    private long tokenTtlSeconds = 40L;

    /** Marge de securite retranchee au TTL pour ne jamais utiliser un token expire de justesse
     * (latence reseau entre la verification du cache et l'usage effectif du token). */
    private long tokenExpiryMarginSeconds = 5L;

    /**
     * Valeur du champ "typetransfer" attendu par checktransfer/checktransferwave. Sa signification
     * n'est pas documentee par SycaPay (texte du PDF source lui-meme inacheve : "(Put example late)",
     * page 9), mais "1" est confirme comme valeur par defaut a conserver telle quelle (2026-09-22) -
     * ne pas modifier sans instruction explicite (modifiable depuis le back-office si SycaPay
     * confirme un jour une autre valeur).
     */
    private String typeTransfer = "1";

    /**
     * TTL du cache de solde utilise UNIQUEMENT par SycaPayBalanceService (endpoint de polling frontend
     * GET /sycapay/balance) - jamais par le flux de paiement, qui verifie toujours le solde en direct.
     */
    private long balanceCacheTtlSeconds = 20L;

    /** SycaPay exige ces deux champs sur checktransfer(wave) mais notre integration est un cashout
     * serveur-a-serveur (pas un checkout avec redirection navigateur) : aucune redirection reelle ne
     * se produit vers ces URLs, elles sont fournies uniquement pour satisfaire le contrat d'API. */
    private String callbackSuccessUrl = "https://assurzen.ci/sycapay/callback/success";

    private String callbackCancelUrl = "https://assurzen.ci/sycapay/callback/cancel";

    private String merchantDisplayName = "AssurZen";

    @PostConstruct
    void validate() {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(merchantId) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(secretKey)) {
            throw new IllegalStateException(
                    "sycapay.enabled=true mais merchant-id/api-key/secret-key ne sont pas tous renseignes "
                            + "(variables d'environnement SYCAPAY_MERCHANT_ID / SYCAPAY_API_KEY / SYCAPAY_SECRET_KEY) - "
                            + "demarrage refuse plutot que d'exposer un module de paiement mal configure.");
        }
    }

    // ------------------------------------------------------------------
    // Getters dynamiques (Dynamic Configuration Engine, module SYCAPAY) - remplacent les accesseurs
    // Lombok generes automatiquement pour ces 12 champs precis, plus les 3 identifiants (base chiffree
    // puis environnement, cf. identifiant()).
    // ------------------------------------------------------------------

    public String getMerchantId() {
        return identifiant("MERCHANT_ID", merchantId);
    }

    public String getApiKey() {
        return identifiant("API_KEY", apiKey);
    }

    public String getSecretKey() {
        return identifiant("SECRET_KEY", secretKey);
    }

    /** Identifiant SycaPay : base chiffree si (et seulement si) la cle de chiffrement est securisee,
     * sinon variable d'environnement. Un echec de lecture/dechiffrement retombe sur l'environnement
     * sans jamais journaliser la valeur. */
    private String identifiant(String suffixe, String valeurEnvironnement) {
        if (dynamicConfigurationService == null || encryptionService == null || !encryptionService.isCleSecurisee()) {
            return valeurEnvironnement;
        }
        try {
            String valeurBase = dynamicConfigurationService.getString(MODULE_PREFIX + suffixe, null);
            return StringUtils.hasText(valeurBase) ? valeurBase : valeurEnvironnement;
        } catch (RuntimeException e) {
            log.error("[SYCAPAY] identifiant {} illisible en base - repli sur la variable d'environnement ({})",
                    MODULE_PREFIX + suffixe, e.getClass().getSimpleName());
            return valeurEnvironnement;
        }
    }

    public boolean isEnabled() {
        return dynamicConfigurationService == null ? enabled
                : dynamicConfigurationService.getBoolean(MODULE_PREFIX + "ENABLED", enabled);
    }

    public String getBaseUrl() {
        return dynamicConfigurationService == null ? baseUrl
                : dynamicConfigurationService.getString(MODULE_PREFIX + "BASE_URL", baseUrl);
    }

    public String getProvider() {
        return dynamicConfigurationService == null ? provider
                : dynamicConfigurationService.getString(MODULE_PREFIX + "PROVIDER", provider);
    }

    public String getCurrency() {
        return dynamicConfigurationService == null ? currency
                : dynamicConfigurationService.getString(MODULE_PREFIX + "CURRENCY", currency);
    }

    public String getLanguage() {
        return dynamicConfigurationService == null ? language
                : dynamicConfigurationService.getString(MODULE_PREFIX + "LANGUAGE", language);
    }

    public long getConnectTimeoutMs() {
        return dynamicConfigurationService == null ? connectTimeoutMs
                : dynamicConfigurationService.getLong(MODULE_PREFIX + "CONNECT_TIMEOUT_MS", connectTimeoutMs);
    }

    public long getReadTimeoutMs() {
        return dynamicConfigurationService == null ? readTimeoutMs
                : dynamicConfigurationService.getLong(MODULE_PREFIX + "READ_TIMEOUT_MS", readTimeoutMs);
    }

    public long getTokenTtlSeconds() {
        return dynamicConfigurationService == null ? tokenTtlSeconds
                : dynamicConfigurationService.getLong(MODULE_PREFIX + "TOKEN_TTL_SECONDS", tokenTtlSeconds);
    }

    public long getTokenExpiryMarginSeconds() {
        return dynamicConfigurationService == null ? tokenExpiryMarginSeconds
                : dynamicConfigurationService.getLong(MODULE_PREFIX + "TOKEN_EXPIRY_MARGIN_SECONDS", tokenExpiryMarginSeconds);
    }

    public String getTypeTransfer() {
        return dynamicConfigurationService == null ? typeTransfer
                : dynamicConfigurationService.getString(MODULE_PREFIX + "TYPE_TRANSFER", typeTransfer);
    }

    public long getBalanceCacheTtlSeconds() {
        return dynamicConfigurationService == null ? balanceCacheTtlSeconds
                : dynamicConfigurationService.getLong(MODULE_PREFIX + "BALANCE_CACHE_TTL_SECONDS", balanceCacheTtlSeconds);
    }

    public String getCallbackSuccessUrl() {
        return dynamicConfigurationService == null ? callbackSuccessUrl
                : dynamicConfigurationService.getString(MODULE_PREFIX + "CALLBACK_SUCCESS_URL", callbackSuccessUrl);
    }

    public String getCallbackCancelUrl() {
        return dynamicConfigurationService == null ? callbackCancelUrl
                : dynamicConfigurationService.getString(MODULE_PREFIX + "CALLBACK_CANCEL_URL", callbackCancelUrl);
    }

    public String getMerchantDisplayName() {
        return dynamicConfigurationService == null ? merchantDisplayName
                : dynamicConfigurationService.getString(MODULE_PREFIX + "MERCHANT_DISPLAY_NAME", merchantDisplayName);
    }
}
