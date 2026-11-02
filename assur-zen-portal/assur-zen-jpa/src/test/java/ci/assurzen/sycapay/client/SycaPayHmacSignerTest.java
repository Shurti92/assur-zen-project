package ci.assurzen.sycapay.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Vecteur de reference calcule independamment avec openssl :
 * printf '100C_MERCHANT001secretKey123' | openssl dgst -sha256 -hmac 'secretKey123' -hex
 * Verifie que l'implementation Java reproduit exactement hash_hmac('sha256', data, key) de PHP
 * (hex minuscule, pas de Base64) sur la formule litterale du PDF SycaPay :
 * data = montant + MarchandID + SECRETKEY ; cle = SECRETKEY.
 */
class SycaPayHmacSignerTest {

    @Test
    void sign_reproduitLeVecteurDeReferenceOpenssl() {
        String signature = SycaPayHmacSigner.sign("100", "C_MERCHANT001", "secretKey123");

        assertEquals("18d9d4f6baab414b7e32f9f41e5c35e9cb87693ec2cfce1eabe1315eccf3942b", signature);
    }

    @Test
    void sign_estDeterministe() {
        String s1 = SycaPayHmacSigner.sign("500", "MID", "KEY");
        String s2 = SycaPayHmacSigner.sign("500", "MID", "KEY");

        assertEquals(s1, s2);
    }

    @Test
    void sign_changeSiLeMontantChange() {
        String s1 = SycaPayHmacSigner.sign("500", "MID", "KEY");
        String s2 = SycaPayHmacSigner.sign("501", "MID", "KEY");

        org.junit.jupiter.api.Assertions.assertNotEquals(s1, s2);
    }

    /**
     * Vecteur de reference communique par SycaPay (2026-09-23) : montant 100, merchantId de test,
     * secretKey de test -> HMAC hexadecimal attendu. Il depend de la VRAIE secretKey : ni elle ni le
     * HMAC attendu (qui vaut signature pour tout virement de 100) ne sont versionnes. Test lance
     * uniquement quand les trois variables sont fournies, ex. :
     * SYCAPAY_MERCHANT_ID=... SYCAPAY_SECRET_KEY=... SYCAPAY_HMAC_VECTEUR_100=... mvn test -Dtest=SycaPayHmacSignerTest
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "SYCAPAY_HMAC_VECTEUR_100", matches = "[0-9a-f]{64}")
    void sign_reproduitLeVecteurDeReferenceSycaPay() {
        String signature = SycaPayHmacSigner.sign("100", System.getenv("SYCAPAY_MERCHANT_ID"), System.getenv("SYCAPAY_SECRET_KEY"));

        assertEquals(System.getenv("SYCAPAY_HMAC_VECTEUR_100"), signature);
    }
}
