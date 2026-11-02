package ci.assurzen.dto;

import ci.assurzen.entities.Tendance;

/**
 * Objet KPI generique reutilise par les 4 dashboards du module (§16 du contrat "KPI Dashboard") -
 * un seul type parametre plutot qu'une classe par domaine. T est BigDecimal (montants - jamais
 * double/float) ou Long (comptages) ou Double (taux/moyennes, pas des montants financiers).
 */
public record KpiValue<T>(
        T valeur,
        T valeurPrecedente,
        T variation,
        Double variationPourcentage,
        Tendance tendance
) {
}
