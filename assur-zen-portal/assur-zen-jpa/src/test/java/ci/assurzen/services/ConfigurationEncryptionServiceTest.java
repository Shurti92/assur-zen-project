package ci.assurzen.services;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.Encryptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chiffrement des configurations sensibles (durcissement du 2026-09-24, identifiants SycaPay en base). */
class ConfigurationEncryptionServiceTest {

    private static final String SECRET = "cle-de-test-longue-de-plus-de-32-caracteres";
    private static final String SEL = "a1b2c3d4e5f60718";

    @Test
    void aesGcm_allerRetour_etChiffreDifferentAChaqueFois() {
        ConfigurationEncryptionService service = new ConfigurationEncryptionService(SECRET, SEL);

        String c1 = service.encrypt("sk_syca_valeur_de_test");
        String c2 = service.encrypt("sk_syca_valeur_de_test");

        assertNotEquals(c1, c2, "IV aleatoire : deux chiffrements du meme texte different");
        assertFalse(c1.contains("sk_syca"));
        assertEquals("sk_syca_valeur_de_test", service.decrypt(c1));
    }

    /** GCM est authentifie : un chiffre altere est rejete, jamais dechiffre en une valeur fausse. */
    @Test
    void chiffreAltere_estRejete_sansFuiteDansLeMessage() {
        ConfigurationEncryptionService service = new ConfigurationEncryptionService(SECRET, SEL);
        String chiffre = service.encrypt("sk_syca_valeur_de_test");
        String altere = chiffre.substring(0, chiffre.length() - 2) + (chiffre.endsWith("00") ? "11" : "00");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.decrypt(altere));
        assertFalse(ex.getMessage().contains(altere));
    }

    @Test
    void autreCle_nePeutPasDechiffrer() {
        String chiffre = new ConfigurationEncryptionService(SECRET, SEL).encrypt("sk_syca_valeur_de_test");
        ConfigurationEncryptionService autre = new ConfigurationEncryptionService("une-autre-cle-longue-de-plus-de-32-car", "0f0e0d0c0b0a0908");

        assertThrows(IllegalStateException.class, () -> autre.decrypt(chiffre));
    }

    /** Valeurs chiffrees avant le durcissement (AES-CBC, cle par defaut historique) : toujours lisibles. */
    @Test
    void valeurHistorique_aesCbcCleParDefaut_resteLisible() {
        String ancien = Encryptors.text(ConfigurationEncryptionService.SECRET_HISTORIQUE, ConfigurationEncryptionService.SEL_HISTORIQUE)
                .encrypt("valeur-historique");

        assertEquals("valeur-historique", new ConfigurationEncryptionService(SECRET, SEL).decrypt(ancien));
    }

    @Test
    void cleSecurisee_uniquementSiFournieParLEnvironnement_longue_etNonParDefaut() {
        assertTrue(new ConfigurationEncryptionService(SECRET, SEL).isCleSecurisee());
        assertFalse(new ConfigurationEncryptionService("", "").isCleSecurisee(), "absente");
        assertFalse(new ConfigurationEncryptionService("trop-courte", SEL).isCleSecurisee(), "< 32 caracteres");
        assertFalse(new ConfigurationEncryptionService(ConfigurationEncryptionService.SECRET_HISTORIQUE, SEL).isCleSecurisee(),
                "valeur par defaut publique");
        assertFalse(new ConfigurationEncryptionService(SECRET, ConfigurationEncryptionService.SEL_HISTORIQUE).isCleSecurisee(),
                "sel par defaut public");
        assertFalse(new ConfigurationEncryptionService(SECRET, "pas-hexa").isCleSecurisee(), "sel non hexadecimal");
    }
}
