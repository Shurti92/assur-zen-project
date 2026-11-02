package ci.assurzen.services;

import ci.assurzen.dto.DashboardAssureResponse;
import ci.assurzen.dto.DashboardGlobalResponse;
import ci.assurzen.dto.DashboardSortieFondsResponse;
import ci.assurzen.dto.DashboardVignetteResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifie que DashboardGlobalService delegue integralement aux 3 services specialises et n'invente/
 * ne recalcule rien lui-meme (§25). */
class DashboardGlobalServiceTest {

    @Test
    void getKpis_delegueAuxTroisServicesEtReutiliseLeursKpisTelsQuels() {
        AssureKpiService assureKpiService = mock(AssureKpiService.class);
        VignetteKpiService vignetteKpiService = mock(VignetteKpiService.class);
        SortieFondsKpiService sortieFondsKpiService = mock(SortieFondsKpiService.class);

        DashboardAssureResponse.AssureKpis assuresKpis = mock(DashboardAssureResponse.AssureKpis.class);
        DashboardVignetteResponse.VignetteKpis vignettesKpis = mock(DashboardVignetteResponse.VignetteKpis.class);
        DashboardSortieFondsResponse.SortieFondsKpis sortiesKpis = mock(DashboardSortieFondsResponse.SortieFondsKpis.class);

        LocalDate debut = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 9, 11);

        when(assureKpiService.getKpis(debut, fin)).thenReturn(
                new DashboardAssureResponse(null, assuresKpis, null, null));
        when(vignetteKpiService.getKpis(debut, fin)).thenReturn(
                new DashboardVignetteResponse(null, vignettesKpis, null, null));
        when(sortieFondsKpiService.getKpis(debut, fin)).thenReturn(
                new DashboardSortieFondsResponse(null, sortiesKpis, null, null));

        DashboardGlobalService service = new DashboardGlobalService(assureKpiService, vignetteKpiService, sortieFondsKpiService);
        DashboardGlobalResponse response = service.getKpis(debut, fin);

        assertSame(assuresKpis, response.assures());
        assertSame(vignettesKpis, response.vignettes());
        assertSame(sortiesKpis, response.sortiesFonds());
        verify(assureKpiService).getKpis(eq(debut), eq(fin));
        verify(vignetteKpiService).getKpis(eq(debut), eq(fin));
        verify(sortieFondsKpiService).getKpis(eq(debut), eq(fin));
    }
}
