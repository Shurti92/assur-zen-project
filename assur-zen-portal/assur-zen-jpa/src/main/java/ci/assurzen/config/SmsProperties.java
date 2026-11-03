package ci.assurzen.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "credential.sms")
@Getter
@Setter
public class SmsProperties {

    private String bearer;
    private String sender;
    private String baseUrl;
    /** Dev/test uniquement : journalise le SMS sans appeler le fournisseur (SMS_MOCK=true). */
    private boolean mock = false;
    /**
     * Liste blanche de numeros (separes par des virgules, SMS_ALLOWED_NUMBERS) pour l'envoi REEL en dev
     * (2026-11-02) : quand elle est renseignee, seuls ces numeros recoivent un vrai SMS, les autres sont journalises
     * sans appel au fournisseur. Vide = aucune restriction (prod/recette).
     */
    private String allowedNumbers;

    public boolean isRestricted() {
        return allowedNumbers != null && !allowedNumbers.isBlank();
    }

    public boolean isAllowed(String numero) {
        if (!isRestricted()) {
            return true;
        }
        String cible = derniers10Chiffres(numero);
        for (String autorise : allowedNumbers.split(",")) {
            if (!autorise.isBlank() && derniers10Chiffres(autorise).equals(cible)) {
                return true;
            }
        }
        return false;
    }

    private static String derniers10Chiffres(String numero) {
        String chiffres = numero == null ? "" : numero.replaceAll("\\D", "");
        return chiffres.length() > 10 ? chiffres.substring(chiffres.length() - 10) : chiffres;
    }
}
