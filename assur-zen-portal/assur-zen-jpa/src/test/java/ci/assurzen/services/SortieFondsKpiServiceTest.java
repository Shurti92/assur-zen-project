package ci.assurzen.services;

import ci.assurzen.dto.DashboardSortieFondsResponse;
import ci.assurzen.entities.FundOutflowStatus;
import ci.assurzen.repositories.FundOutflowRepository;
import ci.assurzen.repositories.PaiementRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Couvre §26 (sorties de fonds) : total, nombre, montant en attente, montant execute, taux
 * d'execution, taux de rejet, evolution, tresorerie, periode sans donnees. */
class SortieFondsKpiServiceTest {

    private record Fixture(SortieFondsKpiService service, FundOutflowRepository fundOutflowRepository, PaiementRepository paiementRepository) {
    }

    private Fixture newFixture() {
        FundOutflowRepository fundOutflowRepository = mock(FundOutflowRepository.class);
        PaiementRepository paiementRepository = mock(PaiementRepository.class);
        AuthService authService = mock(AuthService.class);
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        return new Fixture(new SortieFondsKpiService(fundOutflowRepository, paiementRepository, authService),
                fundOutflowRepository, paiementRepository);
    }

    @Test
    void getKpis_periodeSansDonnees_renvoieDesZerosSansErreur() {
        Fixture fx = newFixture();

        DashboardSortieFondsResponse response = fx.service().getKpis(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31));

        assertEquals(0L, response.kpis().totalSorties().valeur());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.kpis().montantTotal().valeur()));
        assertEquals(0.0, response.kpis().tauxExecution().valeur());
        assertNull(response.kpis().montantPlusGrosseSortie());
        assertEquals(0, response.repartitions().parType().size());
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
        FundOutflowRepository fundOutflowRepository = mock(FundOutflowRepository.class);
        PaiementRepository paiementRepository = mock(PaiementRepository.class);
        AuthService authService = mock(AuthService.class);
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(2L, "user", "USER"));
        SortieFondsKpiService service = new SortieFondsKpiService(fundOutflowRepository, paiementRepository, authService);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getKpis_tauxExecution_ratioExecuteesSurValideesOuApres_pasSurValideesSeules() {
        // 8 executees, 2 encore "validees" (pas encore decaissees) -> valideesOuApres = 10 -> taux = 80%.
        // Si on avait bêtement divise par "validees" seules (2), le taux aurait ete >100%, incoherent.
        Fixture fx = newFixture();
        when(fx.fundOutflowRepository().countByCreatedAtBetweenAndStatusIn(any(), any(), argThat(c -> c != null && c.size() == 4)))
                .thenReturn(8L); // S_EXECUTEE (4 statuts)
        when(fx.fundOutflowRepository().countByCreatedAtBetweenAndStatusIn(any(), any(), argThat(c -> c != null && c.size() == 7)))
                .thenReturn(10L); // S_VALIDEE_OU_APRES (3 + 4 statuts)

        DashboardSortieFondsResponse response = fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11));

        assertEquals(80.0, response.kpis().tauxExecution().valeur());
    }

    @Test
    void getKpis_tauxRejet_ratioRejeteesSurTotal() {
        Fixture fx = newFixture();
        when(fx.fundOutflowRepository().countByCreatedAtBetween(any(), any())).thenReturn(20L);
        when(fx.fundOutflowRepository().countByCreatedAtBetweenAndStatusIn(any(), any(), org.mockito.ArgumentMatchers.eq(java.util.Set.of(FundOutflowStatus.REJECTED))))
                .thenReturn(3L);

        DashboardSortieFondsResponse response = fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11));

        assertEquals(15.0, response.kpis().tauxRejet().valeur());
    }

    @Test
    void getKpis_tresorerie_soldeEtRatioCalculesDepuisEncaissementsEtSorties() {
        Fixture fx = newFixture();
        when(fx.fundOutflowRepository().sumAmount(any(), any())).thenReturn(BigDecimal.valueOf(50_000));
        when(fx.paiementRepository().sumMontant(any(), any())).thenReturn(140_000L);

        DashboardSortieFondsResponse response = fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11));

        assertEquals(0, BigDecimal.valueOf(140_000).compareTo(response.kpis().totalEncaissements().valeur()));
        assertEquals(0, BigDecimal.valueOf(90_000).compareTo(response.kpis().solde().valeur()));
        assertEquals(35.71, response.kpis().ratioSortiesEncaissements().valeur());
    }

    @Test
    void getKpis_delaiMoyenTraitementNull_nEmpecheJamaisLeReste() {
        // Aucune sortie decaissee dans la periode -> AVG(TIMESTAMPDIFF(...)) SQL renvoie NULL, pas de
        // COALESCE dessus (contrairement aux sommes) : verifie que le service le laisse null sans exploser.
        Fixture fx = newFixture();
        when(fx.fundOutflowRepository().delaiMoyenTraitementJours(any(), any())).thenReturn(null);

        DashboardSortieFondsResponse response = fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11));

        assertNull(response.kpis().delaiMoyenTraitementJours());
    }
}
