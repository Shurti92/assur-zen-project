package ci.assurzen.webpush.config;

import ci.assurzen.webpush.VapidTestKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chargement des cles VAPID (2026-09-26) : format npm web-push, desactivation propre sinon, cle
 * privee jamais exposee. */
class WebPushPropertiesTest {

    @Test
    void clesValides_pushConfigureEtClePubliqueRestituee() {
        VapidTestKeys.Paire paire = VapidTestKeys.nouvellePaire();
        WebPushProperties properties = new WebPushProperties();
        properties.getVapid().setPublicKey(paire.publique());
        properties.getVapid().setPrivateKey(paire.privee());
        properties.getVapid().setSubject("mailto:tech@assurzen.ci");

        properties.init();

        assertTrue(properties.isConfigured());
        assertEquals(paire.publique(), properties.keyPair().extractPublicKeyInUncompressedFormAsString());
    }

    @Test
    void clesAbsentes_pushDesactiveSansErreur() {
        WebPushProperties properties = new WebPushProperties();

        properties.init();

        assertFalse(properties.isConfigured());
    }

    @Test
    void clePriveeInvalide_pushDesactiveSansErreur() {
        WebPushProperties properties = new WebPushProperties();
        properties.getVapid().setPublicKey(VapidTestKeys.nouvellePaire().publique());
        properties.getVapid().setPrivateKey("pas-une-cle");
        properties.getVapid().setSubject("mailto:tech@assurzen.ci");

        properties.init();

        assertFalse(properties.isConfigured());
    }

    @Test
    void toString_neRevelePasLaClePrivee() {
        VapidTestKeys.Paire paire = VapidTestKeys.nouvellePaire();
        WebPushProperties.Vapid vapid = new WebPushProperties.Vapid();
        vapid.setPublicKey(paire.publique());
        vapid.setPrivateKey(paire.privee());

        assertFalse(vapid.toString().contains(paire.privee()));
    }
}
