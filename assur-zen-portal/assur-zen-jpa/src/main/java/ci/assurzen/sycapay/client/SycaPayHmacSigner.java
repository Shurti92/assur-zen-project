package ci.assurzen.sycapay.client;

import ci.assurzen.sycapay.exception.SycaPayException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Calcule l'en-tete AUTHORIZATION exige par checktransfer/checktransferwave (section IV.C de la
 * documentation SycaPay, page 9) :
 *
 * <pre>
 * data = montant + MarchandID + SECRETKEY
 * AUTHORIZATION = hash_hmac('sha256', data, SECRETKEY)
 * </pre>
 *
 * Litteral du PDF fourni : concatenation simple (sans separateur), cle HMAC = secretKey (la meme
 * valeur que celle concatenee dans les donnees). hash_hmac de PHP retourne par defaut une chaine
 * hexadecimale en minuscules (pas du Base64) - reproduit ici via HexFormat. Isole dans sa propre
 * classe pure (aucune dependance Spring) pour rester facilement testable avec un vecteur connu.
 * Regle et format hexadecimal CONFIRMES par SycaPay le 2026-09-23 (vecteur de reference montant 100
 * reproduit a l'identique avec les identifiants de test - cf. SycaPayHmacSignerTest). Le montant doit
 * etre EXACTEMENT celui du corps JSON (SycaPayClientImpl signe transferRequest.amount()).
 */
final class SycaPayHmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private SycaPayHmacSigner() {
    }

    static String sign(String amount, String merchantId, String secretKey) {
        String data = amount + merchantId + secretKey;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(rawHmac);
        } catch (GeneralSecurityException e) {
            throw new SycaPayException("Impossible de calculer la signature AUTHORIZATION SycaPay", e);
        }
    }
}
