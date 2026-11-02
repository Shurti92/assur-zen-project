package ci.assurzen.webpush.config;

import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.VAPIDKeyPairs;
import com.zerodeplibs.webpush.key.PrivateKeySources;
import com.zerodeplibs.webpush.key.PublicKeySources;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;

/**
 * Web Push (notifications push navigateur de l'application EP, 2026-09-26) - cles VAPID par
 * environnement. Deux sources, par priorite :
 * 1. variables d'environnement VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY (chargees ici, au demarrage) ;
 * 2. sinon, paire generee automatiquement au premier demarrage et conservee en base, cle privee chiffree
 *    (VapidKeyInitializer) - chaque environnement ayant sa base, chacun a sa paire sans aucune action.
 * Format standard de `npx web-push generate-vapid-keys` (et de deploy/GenerateVapidKeys.java) : cle
 * publique = point P-256 non compresse (65 octets), cle privee = scalaire brut (32 octets), en base64url.
 *
 * Aucune cle utilisable : le push est simplement desactive, le reste de l'application fonctionne
 * normalement. La cle privee n'est jamais journalisee ni exposee (pas de getter public sur la paire de
 * cles, seulement la cle publique).
 */
@Configuration
@ConfigurationProperties(prefix = "webpush")
@Getter
@Setter
@Slf4j
public class WebPushProperties {

    private Vapid vapid = new Vapid();

    /** Duree de conservation d'un message par le service push si l'appareil est hors ligne. */
    private long ttlSeconds = 86_400;

    private int connectTimeoutMs = 5_000;

    private int requestTimeoutMs = 10_000;

    /** Nombre d'echecs consecutifs (hors 404/410, qui desactivent immediatement) avant desactivation. */
    private int maxFailures = 5;

    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private VAPIDKeyPair keyPair;

    /** Cles fournies par l'environnement (source prioritaire). Sans elles, VapidKeyInitializer prend le
     * relais au demarrage (cles en base). */
    @PostConstruct
    public void init() {
        if (clesEnvironnementPresentes()) {
            chargerCles(vapid.getPublicKey(), vapid.getPrivateKey(), "variables d'environnement");
        }
    }

    public boolean clesEnvironnementPresentes() {
        return StringUtils.hasText(vapid.getPublicKey()) || StringUtils.hasText(vapid.getPrivateKey());
    }

    /** Charge une paire (base64url). Faux si elle est invalide ou si le sujet VAPID manque : le push reste
     * alors desactive. source : pour le log uniquement, jamais la valeur. */
    public boolean chargerCles(String clePublique, String clePrivee, String source) {
        if (!StringUtils.hasText(vapid.getSubject())) {
            log.error("[WEBPUSH] VAPID_SUBJECT absent - notifications push desactivees");
            keyPair = null;
            return false;
        }
        try {
            keyPair = VAPIDKeyPairs.of(
                    PrivateKeySources.ofECPrivateKey(privateKeyFromRawScalar(clePrivee.trim())),
                    PublicKeySources.ofUncompressedBytes(Base64.getUrlDecoder().decode(clePublique.trim())));
            log.info("[WEBPUSH] cles VAPID chargees ({}) - notifications push actives", source);
            return true;
        } catch (RuntimeException | java.security.GeneralSecurityException e) {
            // Jamais le message brut : il pourrait contenir un fragment de la cle.
            log.error("[WEBPUSH] cles VAPID invalides ({}, {}) - notifications push desactivees", source, e.getClass().getSimpleName());
            keyPair = null;
            return false;
        }
    }

    /** Vrai si les cles VAPID sont chargees (le push peut etre coupe en plus par WEBPUSH_ENABLED). */
    public boolean isConfigured() {
        return keyPair != null;
    }

    /** Paire de cles pour signer l'en-tete Authorization - usage interne au module webpush uniquement. */
    public VAPIDKeyPair keyPair() {
        return keyPair;
    }

    private static ECPrivateKey privateKeyFromRawScalar(String base64Url) throws java.security.GeneralSecurityException {
        byte[] scalaire = Base64.getUrlDecoder().decode(base64Url);
        if (scalaire.length != 32) {
            throw new java.security.InvalidKeyException("taille inattendue");
        }
        AlgorithmParameters parametres = AlgorithmParameters.getInstance("EC");
        parametres.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec courbe = parametres.getParameterSpec(ECParameterSpec.class);
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalaire), courbe));
    }

    @Getter
    @Setter
    public static class Vapid {
        private String publicKey;
        private String privateKey;
        /** "mailto:..." ou URL https du site - exige par les services push (contact en cas d'abus). */
        private String subject;

        @Override
        public String toString() {
            return "Vapid{publicKey=" + publicKey + ", privateKey=****, subject=" + subject + "}";
        }
    }
}
