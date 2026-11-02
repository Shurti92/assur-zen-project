package ci.assurzen.services;

import ci.assurzen.dto.KpiValue;
import ci.assurzen.entities.Tendance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Construit les KpiValue (§14/§16 du contrat "KPI Dashboard") et calcule la periode precedente
 * (meme duree, immediatement avant), reutilise par VignetteKpiService/SortieFondsKpiService/
 * AssureKpiService - evite de dupliquer ce calcul dans chacun.
 *
 * §14 : "Gerer correctement le cas ou valeurPrecedente = 0" - interprete ici comme
 * variationPourcentage = null (division par zero non definie mathematiquement), tendance restant
 * calculable depuis le seul signe de la variation absolue.
 */
public final class ComparaisonPeriode {

    private ComparaisonPeriode() {
    }

    public record Periode(LocalDate debut, LocalDate fin) {
    }

    public static Periode periodePrecedente(LocalDate debut, LocalDate fin) {
        long jours = ChronoUnit.DAYS.between(debut, fin) + 1;
        LocalDate finPrecedente = debut.minusDays(1);
        LocalDate debutPrecedente = finPrecedente.minusDays(jours - 1);
        return new Periode(debutPrecedente, finPrecedente);
    }

    public static KpiValue<Long> deLong(long valeur, long valeurPrecedente) {
        long variation = valeur - valeurPrecedente;
        return new KpiValue<>(valeur, valeurPrecedente, variation,
                pourcentage(BigDecimal.valueOf(variation), BigDecimal.valueOf(valeurPrecedente)),
                tendance(Long.signum(variation)));
    }

    public static KpiValue<BigDecimal> deBigDecimal(BigDecimal valeurBrute, BigDecimal valeurPrecedenteBrute) {
        BigDecimal valeur = valeurBrute == null ? BigDecimal.ZERO : valeurBrute;
        BigDecimal valeurPrecedente = valeurPrecedenteBrute == null ? BigDecimal.ZERO : valeurPrecedenteBrute;
        BigDecimal variation = valeur.subtract(valeurPrecedente);
        return new KpiValue<>(valeur, valeurPrecedente, variation,
                pourcentage(variation, valeurPrecedente), tendance(variation.signum()));
    }

    public static KpiValue<Double> deDouble(double valeur, double valeurPrecedente) {
        double variation = valeur - valeurPrecedente;
        return new KpiValue<>(round2(valeur), round2(valeurPrecedente), round2(variation),
                pourcentage(BigDecimal.valueOf(variation), BigDecimal.valueOf(valeurPrecedente)),
                tendance((int) Math.signum(variation)));
    }

    private static Double pourcentage(BigDecimal variation, BigDecimal valeurPrecedente) {
        if (valeurPrecedente.signum() == 0) {
            return null;
        }
        return variation.divide(valeurPrecedente, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static Tendance tendance(int signeVariation) {
        if (signeVariation > 0) {
            return Tendance.HAUSSE;
        }
        if (signeVariation < 0) {
            return Tendance.BAISSE;
        }
        return Tendance.STABLE;
    }

    private static double round2(double valeur) {
        return BigDecimal.valueOf(valeur).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
