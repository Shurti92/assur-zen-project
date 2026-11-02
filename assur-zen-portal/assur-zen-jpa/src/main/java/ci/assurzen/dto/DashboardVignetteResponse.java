package ci.assurzen.dto;

import ci.assurzen.entities.CategorieAgeMoto;
import ci.assurzen.entities.CategorieCylindree;
import ci.assurzen.entities.StatutDemandeVignetteMoto;
import ci.assurzen.entities.TypeDemandeVignette;

import java.math.BigDecimal;
import java.util.List;

public record DashboardVignetteResponse(
        DashboardPeriodeResponse periode,
        VignetteKpis kpis,
        VignetteRepartitions repartitions,
        List<VignetteEvolutionItem> evolution
) {

    // Noms des records imbriques prefixes "Vignette" (pas juste "Kpis"/"Repartitions"/"EvolutionItem")
    // pour eviter une collision de schema OpenAPI avec les records homonymes de
    // DashboardSortieFondsResponse/DashboardAssureResponse - Springdoc nomme un schema d'apres le
    // simple-name Java, donc 3 records nommes pareil ecrasaient un seul schema partage dans
    // /v3/api-docs (le JSON reel au runtime restait correct, seule la doc Swagger etait fausse).
    public record VignetteKpis(
            // Volume (§4)
            KpiValue<Long> nombreDemandes,
            KpiValue<Long> nouvellesDemandes,
            KpiValue<Long> renouvellements,
            KpiValue<Long> vignettesEditees,
            KpiValue<Long> vignettesLivrees,
            KpiValue<Long> demandesEnAttente,
            KpiValue<Long> demandesRejetees,
            KpiValue<Long> demandesAnnulees,

            // Financier (§4)
            KpiValue<BigDecimal> montantVignettes,
            KpiValue<BigDecimal> totalFraisOperation,
            KpiValue<BigDecimal> totalFraisLivraison,
            KpiValue<BigDecimal> totalFraisAccessoires,
            KpiValue<BigDecimal> chiffreAffaires,
            KpiValue<BigDecimal> panierMoyen,

            // Performance (§4)
            KpiValue<Double> tauxTransformation,
            KpiValue<Double> tauxEdition,
            KpiValue<Double> tauxLivraison,
            KpiValue<Double> tauxRejet,
            KpiValue<Double> tauxAnnulation,
            /** Non calculable aujourd'hui : le workflow paiement/edition/livraison n'a pas de service
             * derriere (cf. StatutDemandeVignetteMoto), aucune date de transition n'est enregistree
             * au-dela de createdAt/updatedAt. Toujours null tant que cette phase n'existe pas -
             * jamais invente. Pas de KpiValue (une comparaison periode precedente de "null" n'a pas de sens). */
            Double delaiMoyenTraitementJours
    ) {
    }

    public record VignetteRepartitions(
            List<ParTypeDemandeItem> parTypeDemande,
            List<ParCylindreeItem> parCylindree,
            List<ParAgeMotoItem> parAgeMoto,
            List<ParZoneLivraisonItem> parZoneLivraison,
            List<VignetteParStatutItem> parStatut
    ) {
    }

    public record ParTypeDemandeItem(TypeDemandeVignette type, long nombre) {
    }

    public record ParCylindreeItem(CategorieCylindree categorie, long nombre) {
    }

    public record ParAgeMotoItem(CategorieAgeMoto categorie, long nombre) {
    }

    public record ParZoneLivraisonItem(String zoneCode, long nombre) {
    }

    public record VignetteParStatutItem(StatutDemandeVignetteMoto statut, long nombre) {
    }

    public record VignetteEvolutionItem(String periode, long nombreDemandes, BigDecimal montant) {
    }
}
