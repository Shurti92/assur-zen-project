package ci.assurzen.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Chiffrement reversible des valeurs de configuration marquees "sensitive" (password/secret/api_key,
 * identifiants SycaPay...). Reutilise spring-security-crypto, deja une dependance du projet. Distinct
 * du hachage de mot de passe (a sens unique) : la valeur doit pouvoir etre RELUE en clair cote serveur.
 *
 * Durcissement du 2026-09-24 (identifiants SycaPay stockes en base) :
 * - AES-256-GCM (Encryptors.delux : chiffrement AUTHENTIFIE, toute alteration du chiffre est detectee)
 *   au lieu d'AES-CBC (Encryptors.text, sans controle d'integrite) ;
 * - la cle ne doit plus jamais etre la valeur par defaut ecrite dans ce fichier (donc publique dans git) :
 *   APP_CONFIG_ENCRYPTION_SECRET (>= 32 caracteres) et APP_CONFIG_ENCRYPTION_SALT (hexadecimal) doivent
 *   etre fournis par l'environnement. Sans eux, isCleSecurisee() = false et DynamicConfigurationService
 *   REFUSE d'enregistrer toute nouvelle valeur sensible ;
 * - lecture retrocompatible des valeurs chiffrees avant ce durcissement (AES-CBC, cle configuree puis
 *   cle par defaut historique) : elles sont re-chiffrees en AES-GCM a leur prochaine modification.
 * Jamais de valeur (claire ou chiffree) dans un log ni dans un message d'exception.
 */
@Service
@Slf4j
public class ConfigurationEncryptionService {

    /** Anciennes valeurs par defaut, publiques (versionnees) : conservees UNIQUEMENT pour relire les
     * valeurs chiffrees avant le 2026-09-24, jamais pour chiffrer une nouvelle valeur de facon "securisee". */
    static final String SECRET_HISTORIQUE = "assurzen-config-default-secret";
    static final String SEL_HISTORIQUE = "5a736475723a656e";
    static final int LONGUEUR_MIN_SECRET = 32;

    private final TextEncryptor encryptor;
    private final TextEncryptor legacyEncryptor;
    private final TextEncryptor legacyDefaultEncryptor;
    private final boolean cleSecurisee;

    public ConfigurationEncryptionService(
            @Value("${app.config.encryption-secret:}") String secret,
            @Value("${app.config.encryption-salt:}") String hexSalt) {
        this.cleSecurisee = estSecurisee(secret, hexSalt);
        String cle = StringUtils.hasText(secret) ? secret : SECRET_HISTORIQUE;
        // Un sel mal forme ferait echouer la construction (donc le demarrage) : on retombe sur le sel
        // historique, et la cle est de toute facon marquee non securisee (estSecurisee).
        String sel = selValide(hexSalt) ? hexSalt : SEL_HISTORIQUE;
        this.encryptor = Encryptors.delux(cle, sel);
        this.legacyEncryptor = Encryptors.text(cle, sel);
        this.legacyDefaultEncryptor = Encryptors.text(SECRET_HISTORIQUE, SEL_HISTORIQUE);
        if (!cleSecurisee) {
            log.warn("Cle de chiffrement des configurations sensibles NON securisee (APP_CONFIG_ENCRYPTION_SECRET/SALT absents, "
                    + "par defaut ou trop courts) - l'enregistrement de nouvelles valeurs sensibles est refuse");
        }
    }

    static boolean estSecurisee(String secret, String hexSalt) {
        return StringUtils.hasText(secret)
                && secret.length() >= LONGUEUR_MIN_SECRET
                && !SECRET_HISTORIQUE.equals(secret)
                && selValide(hexSalt)
                && !SEL_HISTORIQUE.equalsIgnoreCase(hexSalt);
    }

    private static boolean selValide(String hexSalt) {
        return StringUtils.hasText(hexSalt) && hexSalt.matches("(?i)[0-9a-f]{16,}") && hexSalt.length() % 2 == 0;
    }

    /** true si la cle vient de l'environnement et n'est pas la valeur par defaut publique. */
    public boolean isCleSecurisee() {
        return cleSecurisee;
    }

    public String encrypt(String plainText) {
        return plainText == null ? null : encryptor.encrypt(plainText);
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            return encryptor.decrypt(cipherText);
        } catch (RuntimeException gcm) {
            try {
                return legacyEncryptor.decrypt(cipherText);
            } catch (RuntimeException cbc) {
                try {
                    return legacyDefaultEncryptor.decrypt(cipherText);
                } catch (RuntimeException historique) {
                    // Jamais le chiffre ni la cause (qui pourrait en contenir un fragment) dans le message.
                    throw new IllegalStateException("Valeur de configuration sensible indechiffrable avec la cle configuree");
                }
            }
        }
    }
}
