package ci.assurzen.services;

import ci.assurzen.dto.KpiValue;
import ci.assurzen.entities.Tendance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Couvre §14 (comparaison periode precedente, y compris "valeurPrecedente = 0") et le calcul de
 * periode precedente (§6/§13) partages par les 3 services KPI. */
class ComparaisonPeriodeTest {

    // ---------------------------------------------------------------- periodePrecedente()

    @Test
    void periodePrecedente_memeDureeImmediatementAvant() {
        var periode = ComparaisonPeriode.periodePrecedente(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertEquals(LocalDate.of(2026, 8, 2), periode.debut());
        assertEquals(LocalDate.of(2026, 8, 31), periode.fin());
    }

    @Test
    void periodePrecedente_uneSeuleJournee() {
        var periode = ComparaisonPeriode.periodePrecedente(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 11));
        assertEquals(LocalDate.of(2026, 9, 10), periode.debut());
        assertEquals(LocalDate.of(2026, 9, 10), periode.fin());
    }

    // ---------------------------------------------------------------- deLong() / tendance

    @Test
    void deLong_hausse() {
        KpiValue<Long> v = ComparaisonPeriode.deLong(150, 100);
        assertEquals(150L, v.valeur());
        assertEquals(100L, v.valeurPrecedente());
        assertEquals(50L, v.variation());
        assertEquals(50.0, v.variationPourcentage());
        assertEquals(Tendance.HAUSSE, v.tendance());
    }

    @Test
    void deLong_baisse() {
        KpiValue<Long> v = ComparaisonPeriode.deLong(80, 100);
        assertEquals(-20L, v.variation());
        assertEquals(-20.0, v.variationPourcentage());
        assertEquals(Tendance.BAISSE, v.tendance());
    }

    @Test
    void deLong_stable() {
        KpiValue<Long> v = ComparaisonPeriode.deLong(100, 100);
        assertEquals(0L, v.variation());
        assertEquals(0.0, v.variationPourcentage());
        assertEquals(Tendance.STABLE, v.tendance());
    }

    @Test
    void deLong_valeurPrecedenteZeroEtValeurPositive_pourcentageEstNull_maisTendanceResteHausse() {
        // §14 : "gerer correctement le cas ou valeurPrecedente = 0" - division non definie -> null,
        // mais la tendance reste deductible du seul signe de la variation absolue.
        KpiValue<Long> v = ComparaisonPeriode.deLong(5, 0);
        assertEquals(5L, v.variation());
        assertNull(v.variationPourcentage());
        assertEquals(Tendance.HAUSSE, v.tendance());
    }

    @Test
    void deLong_valeurEtValeurPrecedenteToutesDeuxZero_stableEtPourcentageNull() {
        KpiValue<Long> v = ComparaisonPeriode.deLong(0, 0);
        assertEquals(Tendance.STABLE, v.tendance());
        assertNull(v.variationPourcentage());
    }

    // ---------------------------------------------------------------- deBigDecimal() - montants

    @Test
    void deBigDecimal_calculeCorrectement() {
        KpiValue<BigDecimal> v = ComparaisonPeriode.deBigDecimal(BigDecimal.valueOf(11250), BigDecimal.valueOf(9000));
        assertEquals(0, BigDecimal.valueOf(2250).compareTo(v.variation()));
        assertEquals(25.0, v.variationPourcentage());
        assertEquals(Tendance.HAUSSE, v.tendance());
    }

    @Test
    void deBigDecimal_valeursNulles_traiteesCommeZeroPasNPE() {
        KpiValue<BigDecimal> v = ComparaisonPeriode.deBigDecimal(null, null);
        assertEquals(0, BigDecimal.ZERO.compareTo(v.valeur()));
        assertEquals(0, BigDecimal.ZERO.compareTo(v.valeurPrecedente()));
        assertEquals(Tendance.STABLE, v.tendance());
        assertNull(v.variationPourcentage());
    }

    // ---------------------------------------------------------------- deDouble() - taux

    @Test
    void deDouble_arrondiA2Decimales() {
        KpiValue<Double> v = ComparaisonPeriode.deDouble(33.33333, 25.0);
        assertEquals(33.33, v.valeur());
        assertEquals(8.33, v.variation());
        assertEquals(Tendance.HAUSSE, v.tendance());
    }
}
