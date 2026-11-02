package ci.assurzen.dto;

import java.util.List;

public record DashboardKPIs(
        long totalContracts,
        long signedContracts,
        long pendingContracts,
        long production,
        long totalCommission,
        long paidCommission,
        long pendingCommission,
        long partnerShare,
        /** partnerShare (pas commissionAmount) des contrats signed+pending - c'est l'argent
         * effectivement du au partenaire (cf. CommissionService.pay()/Ledger), contrairement a
         * pendingCommission ci-dessus qui reste base sur commissionAmount (commission comptable
         * interne). A utiliser comme reference pour "solde de commission en attente" cote partenaire -
         * meme calcul que GET /partenaires/{id}/commissions/partial-payment-requests. */
        long pendingPartnerShare,
        double conversionRate,
        long monthlyObjective,
        double objectiveProgress,
        /** partnerShare (pas commissionAmount) des contrats signed+paid - montant reellement deja
         * verse au partenaire (cf. CommissionService.recordPayment: payment.amount = partnerShare).
         * Comble le trou laisse par paidCommission ci-dessus, reste base sur commissionAmount. */
        long paidPartnerShare,
        /** Nombre et montant (somme de premium, pas partnerShare/commissionAmount - c'est la prime
         * elle-meme, encaissee par le partenaire aupres du client, qui reste due a la compagnie) des
         * contrats signed dont la prime n'a pas encore ete reversee a la compagnie
         * (StatutReversementPrime.non_reverse). Flux d'argent inverse de paidPartnerShare/
         * pendingPartnerShare - decision produit du 2026-08-22. */
        long primeNonReverseeCount,
        long primeNonReverseeMontant,
        /** Nombre total d'avenants (tous types confondus) sur les contrats du perimetre - decision
         * produit du 2026-08-23. Detail par contrat/partenaire via GET /contracts/{id}/avenants,
         * GET /contracts/avenants, GET /partenaires/{id}/contracts/avenants. */
        long avenantsCount,
        /** Memes indicateurs sur la periode precedente de meme duree (mois, trimestre ou annee
         * calendaire precedents), pour les tendances - null pour period=all (2026-10-03). */
        Long previousTotalContracts,
        Long previousSignedContracts,
        Long previousProduction,
        Long previousPartnerShare,
        /** Production (prime des contrats signed) de la periode par branche (ProductType), triee par
         * production decroissante ; part = fraction 0..1 de la production de la periode. */
        List<BrancheRepartition> repartitionParBranche
) {

    public record BrancheRepartition(
            Long brancheId,
            String code,
            String branche,
            String couleur,
            long nbContrats,
            long production,
            double part
    ) {
    }
}
