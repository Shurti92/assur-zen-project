package ci.assurzen.sycapay.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mode test des virements SycaPay (2026-09-24) : hors production uniquement, jamais a moitie configure. */
class SycaPayModeTestTest {

    @Test
    void actif_horsProduction_avecNumeroEtMontant() {
        SycaPayModeTest mode = new SycaPayModeTest(true, "0700000099", 100L, new String[]{"dev"});

        assertTrue(mode.isActif());
        assertEquals("0700000099", mode.getTelephone());
        assertEquals(100L, mode.getMontant());
    }

    /** Garde-fou principal : jamais actif en production, meme si la variable est a true. */
    @Test
    void jamaisActif_siUnProfilDeProductionEstActif() {
        for (String[] profils : new String[][]{{"prod"}, {"PROD"}, {"dev", "prod"}, {"preprod"}, {"production"}}) {
            SycaPayModeTest mode = new SycaPayModeTest(true, "0700000099", 100L, profils);

            assertFalse(mode.isActif(), String.join(",", profils));
            assertNull(mode.getTelephone());
            assertNull(mode.getMontant());
        }
    }

    @Test
    void inactif_parDefaut() {
        assertFalse(new SycaPayModeTest(false, "0700000099", 100L, new String[]{"dev"}).isActif());
    }

    /** Demande hors prod mais mal configure : demarrage refuse plutot qu'un vrai virement par erreur. */
    @Test
    void demandeSansNumeroValideOuMontantPositif_refuseLeDemarrage() {
        assertThrows(IllegalStateException.class, () -> new SycaPayModeTest(true, "", 100L, new String[]{"dev"}));
        assertThrows(IllegalStateException.class, () -> new SycaPayModeTest(true, "07 57 abc", 100L, new String[]{"dev"}));
        assertThrows(IllegalStateException.class, () -> new SycaPayModeTest(true, "0700000099", 0L, new String[]{"dev"}));
    }
}
