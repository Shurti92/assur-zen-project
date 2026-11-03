package ci.assurzen.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Liste blanche des numeros pour l'envoi reel de SMS en dev (2026-11-02). */
class SmsPropertiesTest {

    @Test
    void sansListe_tousLesNumerosSontAutorises() {
        SmsProperties props = new SmsProperties();
        assertFalse(props.isRestricted());
        assertTrue(props.isAllowed("0700000000"));
        assertTrue(props.isAllowed(null));
    }

    @Test
    void avecListe_seulsLesNumerosListesSontAutorises_quelleQueSoitLaPresentation() {
        SmsProperties props = new SmsProperties();
        props.setAllowedNumbers("0700000011, 0700000012");
        assertTrue(props.isRestricted());
        assertTrue(props.isAllowed("0700000011"));
        assertTrue(props.isAllowed("+2250700000011"));
        assertTrue(props.isAllowed("225 07 47 30 43 86"));
        assertFalse(props.isAllowed("0500000001"));
        assertFalse(props.isAllowed(null));
        assertFalse(props.isAllowed(""));
    }
}
