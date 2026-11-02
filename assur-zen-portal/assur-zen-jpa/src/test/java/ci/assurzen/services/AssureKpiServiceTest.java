package ci.assurzen.services;

import ci.assurzen.dto.DashboardAssureResponse;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Couvre §26 (assures) : total, nouveaux, actifs, inactifs, multi-equipes, taux multi-equipement,
 * evolution, comparaison periode precedente. */
class AssureKpiServiceTest {

    private record Fixture(AssureKpiService service, InsuredRepository insuredRepository, ContractRepository contractRepository) {
    }

    private Fixture newFixture() {
        InsuredRepository insuredRepository = mock(InsuredRepository.class);
        ContractRepository contractRepository = mock(ContractRepository.class);
        AuthService authService = mock(AuthService.class);
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        // moyenneContratsParAssureActifAvant() est deballee en double primitif cote service : doit
        // toujours etre stubbee (jamais null), meme quand un test ne teste pas cette valeur precise.
        when(contractRepository.moyenneContratsParAssureActifAvant(any())).thenReturn(0.0);
        return new Fixture(new AssureKpiService(insuredRepository, contractRepository, authService), insuredRepository, contractRepository);
    }

    @Test
    void getKpis_periodeSansDonnees_renvoieDesZerosSansErreur() {
        Fixture fx = newFixture();
        DashboardAssureResponse response = fx.service().getKpis(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31));

        assertEquals(0L, response.kpis().totalAssures().valeur());
        assertEquals(0L, response.kpis().nouveauxAssures().valeur());
        assertEquals(0.0, response.kpis().tauxMultiEquipement().valeur());
        assertEquals(0, response.repartitions().parVille().size());
        assertEquals(0, response.evolution().size());
    }

    @Test
    void getKpis_dateDebutPosterieureADateFin_leve400() {
        Fixture fx = newFixture();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().getKpis(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getKpis_sansDroitsAdministrateur_leve403() {
        InsuredRepository insuredRepository = mock(InsuredRepository.class);
        ContractRepository contractRepository = mock(ContractRepository.class);
        AuthService authService = mock(AuthService.class);
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(2L, "user", "USER"));
        AssureKpiService service = new AssureKpiService(insuredRepository, contractRepository, authService);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getKpis_totalEtNouveaux_reprisDirectementDesRepositories() {
        Fixture fx = newFixture();
        when(fx.insuredRepository().count()).thenReturn(100L);
        when(fx.contractRepository().countNouveauxAssures(any(), any())).thenReturn(12L);

        DashboardAssureResponse response = fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11));

        assertEquals(100L, response.kpis().totalAssures().valeur());
        assertEquals(12L, response.kpis().nouveauxAssures().valeur());
        // totalPrecedent = totalActuel - nouveaux (Insured non historise, cf. javadoc du service).
        assertEquals(88L, response.kpis().totalAssures().valeurPrecedente());
    }

    @Test
    void getKpis_inactifs_estLeComplementDesActifsParRapportAuTotal() {
        Fixture fx = newFixture();
        when(fx.insuredRepository().count()).thenReturn(50L);
        when(fx.contractRepository().countAssuresActifsAvant(any(), any())).thenReturn(30L);

        DashboardAssureResponse response = fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11));

        assertEquals(30L, response.kpis().assuresActifs().valeur());
        assertEquals(20L, response.kpis().assuresInactifs().valeur());
    }

    @Test
    void getKpis_tauxMultiEquipement_ratioMultiEquipesSurActifs() {
        Fixture fx = newFixture();
        when(fx.contractRepository().countAssuresActifsAvant(any(), any())).thenReturn(11L);
        when(fx.contractRepository().countAssuresMultiEquipesAvant(any())).thenReturn(4L);

        DashboardAssureResponse response = fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11));

        assertEquals(4L, response.kpis().assuresMultiEquipes().valeur());
        assertEquals(36.36, response.kpis().tauxMultiEquipement().valeur());
    }

    @Test
    void getKpis_evolution_totalCumuleProgresseAvecLesNouveauxDeChaquePoint() {
        Fixture fx = newFixture();
        ci.assurzen.repositories.projection.PeriodeCount point1 = mock(ci.assurzen.repositories.projection.PeriodeCount.class);
        when(point1.getPeriode()).thenReturn("2026-07");
        when(point1.getTotal()).thenReturn(5L);
        ci.assurzen.repositories.projection.PeriodeCount point2 = mock(ci.assurzen.repositories.projection.PeriodeCount.class);
        when(point2.getPeriode()).thenReturn("2026-08");
        when(point2.getTotal()).thenReturn(13L);

        when(fx.contractRepository().countAssuresConnusAvant(any())).thenReturn(0L);
        when(fx.contractRepository().evolutionNouveauxAssuresParMois(any(), any())).thenReturn(java.util.List.of(point1, point2));

        DashboardAssureResponse response = fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(2, response.evolution().size());
        assertEquals(5L, response.evolution().get(0).nouveauxAssures());
        assertEquals(5L, response.evolution().get(0).totalAssures());
        assertEquals(13L, response.evolution().get(1).nouveauxAssures());
        assertEquals(18L, response.evolution().get(1).totalAssures());
    }
}
