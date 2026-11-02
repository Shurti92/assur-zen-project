package ci.assurzen.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSortieFondsResponse(
        DashboardPeriodeResponse periode,
        SortieFondsKpis kpis,
        SortieFondsRepartitions repartitions,
        List<SortieFondsEvolutionItem> evolution
) {

    // Prefixe "SortieFonds" (pas juste "Kpis"/"Repartitions"/"EvolutionItem") pour eviter une
    // collision de schema OpenAPI avec les records homonymes des 2 autres dashboards - cf. javadoc
    // equivalent dans DashboardVignetteResponse.
    public record SortieFondsKpis(
            // Volume (§7)
            KpiValue<Long> totalSorties,
            KpiValue<Long> enAttente,
            KpiValue<Long> validees,
            KpiValue<Long> executees,
            KpiValue<Long> rejetees,
            KpiValue<Long> annulees,

            // Financier (§7)
            KpiValue<BigDecimal> montantTotal,
            KpiValue<BigDecimal> montantEnAttente,
            KpiValue<BigDecimal> montantValide,
            KpiValue<BigDecimal> montantExecute,
            KpiValue<BigDecimal> montantRejete,
            KpiValue<BigDecimal> montantAnnule,
            KpiValue<BigDecimal> montantMoyenParSortie,
            /** Maximum instantane, pas de sens de le comparer a la periode precedente comme une somme -
             * pas de KpiValue. */
            BigDecimal montantPlusGrosseSortie,

            // Performance (§7) - dates reelles disponibles sur FundOutflow (submittedAt/.../disbursedAt),
            // contrairement aux vignettes : delai moyen calculable et comparable a la periode precedente.
            KpiValue<Double> tauxExecution,
            KpiValue<Double> tauxRejet,
            KpiValue<Double> delaiMoyenTraitementJours,

            // Tresorerie (§9) - encaissements via Paiement (contrats), seule donnee d'encaissement
            // existante dans le projet. Absent (pas invente) si aucune donnee sur la periode.
            KpiValue<BigDecimal> totalEncaissements,
            KpiValue<BigDecimal> solde,
            KpiValue<Double> ratioSortiesEncaissements
    ) {
    }

    public record SortieFondsRepartitions(
            List<ParTypeItem> parType,
            List<ParBeneficiaireItem> parBeneficiaire,
            List<SortieFondsParStatutItem> parStatut
            // Pas de "par produit/partenaire/agence/moyen de paiement" - aucun de ces axes n'existe sur
            // FundOutflow (verifie dans le code, pas suppose). parBeneficiaire couvre beneficiaryType/Id/Label.
    ) {
    }

    public record ParTypeItem(String typeCode, long nombre, BigDecimal montant) {
    }

    public record ParBeneficiaireItem(String beneficiaire, long nombre, BigDecimal montant) {
    }

    public record SortieFondsParStatutItem(String statut, long nombre, BigDecimal montant) {
    }

    public record SortieFondsEvolutionItem(String periode, long nombre, BigDecimal montant) {
    }
}
