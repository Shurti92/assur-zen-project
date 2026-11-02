package ci.assurzen.services;

import ci.assurzen.dto.DashboardGlobalResponse;
import ci.assurzen.dto.DashboardPeriodeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Compose les 3 dashboards specialises (§25) - ne recalcule rien, delegue integralement a
 * AssureKpiService/VignetteKpiService/SortieFondsKpiService (chacun garde sa propre securite/
 * validation/log, cf. leur javadoc). Premiere version : assures + vignettes + sorties de fonds
 * uniquement, comme demande - contrats/encaissements/sinistres/reclamations viendront plus tard sans
 * toucher a cette classe (juste un nouveau champ + un nouvel appel delegue).
 */
@Service
@RequiredArgsConstructor
public class DashboardGlobalService {

    private final AssureKpiService assureKpiService;
    private final VignetteKpiService vignetteKpiService;
    private final SortieFondsKpiService sortieFondsKpiService;

    public DashboardGlobalResponse getKpis(LocalDate dateDebut, LocalDate dateFin) {
        var assures = assureKpiService.getKpis(dateDebut, dateFin).kpis();
        var vignettes = vignetteKpiService.getKpis(dateDebut, dateFin).kpis();
        var sortiesFonds = sortieFondsKpiService.getKpis(dateDebut, dateFin).kpis();

        return new DashboardGlobalResponse(new DashboardPeriodeResponse(dateDebut, dateFin), assures, vignettes, sortiesFonds);
    }
}
