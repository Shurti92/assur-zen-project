package ci.assurzen.dto;

import java.util.List;

/**
 * Indicateurs agreges dedies au tableau de bord back-office Angular (distinct de DashboardKPIs,
 * qui reste l'API mobile scopee au commercial connecte). Vue globale tous commerciaux/partenaires
 * confondus, deja agregee cote backend pour eviter au frontend de rapatrier des centaines de lignes.
 */
public record DashboardBackOfficeKpis(
        Volumes volumes,
        Finance finance,
        ObjectifGlobal objectifGlobal,
        CycleDeVie cycleDeVie,
        List<ProduitRepartition> repartitionParProduit,
        ObjectifParApporteur objectifParApporteur
) {

    public record Volumes(
            long totalContracts,
            long signedContracts,
            long pendingContracts,
            double conversionRate
    ) {
    }

    public record Finance(
            long production,
            long totalCommission,
            long paidCommission,
            long pendingCommission,
            long partnerShare,
            /** Nombre et montant (somme de premium) des contrats signes, tous partenaires confondus,
             * dont la prime encaissee par le partenaire n'a pas encore ete reversee a la compagnie.
             * Decision produit du 2026-08-22, meme calcul que DashboardKPIs.primeNonReverseeCount/
             * Montant mais non scope a un partenaire. */
            long primeNonReverseeCount,
            long primeNonReverseeMontant,
            /** Nombre total d'avenants (tous types confondus), tous partenaires confondus, sur les
             * contrats signes de la periode - decision produit du 2026-08-23. */
            long avenantsCount
    ) {
    }

    public record ObjectifGlobal(
            long montant,
            double progression
    ) {
    }

    public record CycleDeVie(
            long realises,
            long echeanceProche,
            long nonRenouveles
    ) {
    }

    public record ProduitRepartition(
            Long productId,
            String productName,
            long count
    ) {
    }

    public record ObjectifParApporteur(
            long totalObjectif,
            long totalRealise,
            double tauxGlobal,
            List<PartenaireObjectif> partenaires
    ) {
    }

    public record PartenaireObjectif(
            Long partenaireId,
            String nom,
            String type,
            long objectif,
            long realise,
            double taux
    ) {
    }
}
