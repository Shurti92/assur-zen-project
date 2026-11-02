package ci.assurzen.dto;

/**
 * Compose les Kpis des 3 dashboards specialises (§25) - reutilise leurs records nested tels quels,
 * DashboardGlobalService ne recalcule rien, il delegue a AssureKpiService/VignetteKpiService/
 * SortieFondsKpiService et assemble. Repartitions/evolution volontairement absentes ici (deja
 * disponibles sur chaque dashboard specialise) pour rester une vue de synthese legere.
 */
public record DashboardGlobalResponse(
        DashboardPeriodeResponse periode,
        DashboardAssureResponse.AssureKpis assures,
        DashboardVignetteResponse.VignetteKpis vignettes,
        DashboardSortieFondsResponse.SortieFondsKpis sortiesFonds
) {
}
