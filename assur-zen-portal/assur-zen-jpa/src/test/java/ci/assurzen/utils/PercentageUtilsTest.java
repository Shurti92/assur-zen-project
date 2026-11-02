package ci.assurzen.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Couvre le bornage d'un taux calcule (realise/objectif*100) avant persistance dans une colonne
 * DECIMAL(precision, scale) - bug reel du 2026-09-17 (CommercialPerformance.achievementRate,
 * PrimeObjectif.tauxRealisation).
 */
class PercentageUtilsTest {

    @Test
    void maxRepresentable_precision9Scale4_rendLaValeurMaxDeLaColonneAchievementRate() {
        assertEquals(0, BigDecimal.valueOf(99999.9999).compareTo(PercentageUtils.maxRepresentable(9, 4)));
    }

    @Test
    void maxRepresentable_precision7Scale2_rendLaValeurMaxDeLaColonneTauxRealisation() {
        assertEquals(0, BigDecimal.valueOf(99999.99).compareTo(PercentageUtils.maxRepresentable(7, 2)));
    }

    @Test
    void borner_valeurDansLIntervalle_nEstPasModifiee() {
        BigDecimal resultat = PercentageUtils.borner(BigDecimal.valueOf(125.5), 9, 4);
        assertEquals(0, BigDecimal.valueOf(125.5).compareTo(resultat));
    }

    @Test
    void borner_valeurAstronomique_estEcreteALaValeurMax() {
        BigDecimal resultat = PercentageUtils.borner(BigDecimal.valueOf(10_000_000), 9, 4);
        assertEquals(0, BigDecimal.valueOf(99999.9999).compareTo(resultat));
    }

    @Test
    void borner_valeurNegativeAstronomique_estEcreteALaValeurMinNegative() {
        BigDecimal resultat = PercentageUtils.borner(BigDecimal.valueOf(-10_000_000), 9, 4);
        assertEquals(0, BigDecimal.valueOf(-99999.9999).compareTo(resultat));
    }

    @Test
    void borner_null_renvoieNull() {
        assertNull(PercentageUtils.borner(null, 9, 4));
    }
}
