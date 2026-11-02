package ci.assurzen.utils;

import java.math.BigDecimal;

/**
 * Un taux calcule (realise / objectif x 100) n'a aucune borne superieure naturelle - un objectif fixe
 * anormalement bas par rapport au realise (ex. objectif=1 FCFA, realise=100 000 FCFA) produit un taux
 * de plusieurs millions de %, hors de portee de n'importe quelle colonne DECIMAL(precision, scale)
 * raisonnable. Bug reel constate le 2026-09-17 sur CommercialPerformance.achievementRate
 * (DECIMAL(9,4)) : la persistance levait Data truncation ("Out of range value"), remontee en 409
 * generique par GlobalExceptionHandler.contrainteBaseViolee() plutot qu'une erreur exploitable.
 * PrimeObjectif.tauxRealisation (DECIMAL(7,2), encore plus etroite) est expose au meme risque via le
 * meme calcul (PartenaireService.computeTauxRealisation). Borner AVANT persistance plutot que valider
 * l'objectif en amont : un vrai depassement d'objectif (ex. 300% de realisation) reste un cas metier
 * legitime, seule la valeur astronomique hors de portee de la colonne doit etre ecretee.
 */
public final class PercentageUtils {

    private PercentageUtils() {
    }

    /** Plus grande valeur positive representable par une colonne DECIMAL(precision, scale). */
    public static BigDecimal maxRepresentable(int precision, int scale) {
        return BigDecimal.TEN.pow(precision).subtract(BigDecimal.ONE).movePointLeft(scale);
    }

    /** Borne `valeur` a l'intervalle [-max, +max] representable par une colonne DECIMAL(precision, scale)
     * - jamais applique a un champ transitoire (DTO-only, non persiste), uniquement avant d'ecrire dans
     * une colonne physique de cette precision. */
    public static BigDecimal borner(BigDecimal valeur, int precision, int scale) {
        if (valeur == null) {
            return null;
        }
        BigDecimal max = maxRepresentable(precision, scale);
        if (valeur.compareTo(max) > 0) {
            return max;
        }
        BigDecimal min = max.negate();
        return valeur.compareTo(min) < 0 ? min : valeur;
    }
}
