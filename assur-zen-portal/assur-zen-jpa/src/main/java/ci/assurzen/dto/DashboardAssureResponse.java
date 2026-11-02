package ci.assurzen.dto;

import ci.assurzen.entities.TypeClient;

import java.util.List;

public record DashboardAssureResponse(
        DashboardPeriodeResponse periode,
        AssureKpis kpis,
        AssureRepartitions repartitions,
        List<AssureEvolutionItem> evolution
) {

    // Prefixe "Assure" (pas juste "Kpis"/"Repartitions"/"EvolutionItem") pour eviter une collision
    // de schema OpenAPI avec les records homonymes des 2 autres dashboards - cf. javadoc equivalent
    // dans DashboardVignetteResponse.
    public record AssureKpis(
            KpiValue<Long> totalAssures,
            /** Insured n'a pas de createdAt en base - "nouveaux" est deduit via la date de creation du
             * premier contrat de l'assure sur la periode (seule donnee temporelle disponible), pas une
             * vraie date d'enregistrement de la fiche assure. Limite documentee, pas cachee. */
            KpiValue<Long> nouveauxAssures,
            /** Actif = au moins un contrat SIGNE dont l'echeance n'est pas depassee (decision produit
             * du 2026-09-11, coherent avec CycleDeVie de DashboardBackOfficeKpis). */
            KpiValue<Long> assuresActifs,
            KpiValue<Long> assuresInactifs,
            /** §10 : 2+ contrats SIGNES, meme produit ou non - distinct du multi-equipement (§11). */
            KpiValue<Long> assuresPlusieursContrats,
            /** §11 : 2+ contrats SIGNES actifs sur des produits DISTINCTS (decision produit du
             * 2026-09-11 - aucune definition preexistante dans le code). */
            KpiValue<Long> assuresMultiEquipes,
            KpiValue<Double> nombreMoyenContratsParAssure,
            KpiValue<Double> tauxCroissance,
            KpiValue<Double> tauxMultiEquipement
    ) {
    }

    public record AssureRepartitions(
            List<ParVilleItem> parVille,
            List<ParTypeClientItem> parTypeClient
            // Pas de "par produit/partenaire/canal d'acquisition" - Insured n'a pas de canal, et son seul
            // lien vers produit/partenaire passe par Contract (ambigu pour un assure multi-contrats).
    ) {
    }

    public record ParVilleItem(String ville, long nombre) {
    }

    public record ParTypeClientItem(TypeClient typeClient, long nombre) {
    }

    public record AssureEvolutionItem(String periode, long nouveauxAssures, long totalAssures) {
    }
}
