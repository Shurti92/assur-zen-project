package ci.assurzen.sycapay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Un element du tableau "Resultats" de checkcashoutsolde.php (un par operateur/wallet). Aucun champ
 * sensible ici (marchandid n'est pas un secret - contrairement a apiKey/secretKey/token, il figure
 * deja dans les headers de toutes les autres requetes). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SycaPayBalanceEntry(
        String identifiant,
        String solde,
        String operateur,
        String isopays,
        String marchandid
) {
}
