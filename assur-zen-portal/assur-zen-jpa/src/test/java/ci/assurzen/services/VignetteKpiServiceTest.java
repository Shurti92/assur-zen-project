package ci.assurzen.services;

import ci.assurzen.dto.DashboardVignetteResponse;
import ci.assurzen.entities.StatutDemandeVignetteMoto;
import ci.assurzen.entities.TypeDemandeVignette;
import ci.assurzen.repositories.DemandeVignetteMotoRepository;
import ci.assurzen.repositories.projection.LibelleCount;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Couvre §26 (vignettes) : nombre de demandes, nouvelles/renouvellements, montants, frais
 * accessoires, taux de livraison/rejet, evolution, comparaison periode precedente, periode vide,
 * dateDebut > dateFin. Repository entierement mocke - agregation SQL simulee via stubs, pas de
 * findAll()+stream() a tester ici (deja garanti par la signature des methodes appelees). */
class VignetteKpiServiceTest {

    private record Fixture(VignetteKpiService service, DemandeVignetteMotoRepository repository) {
    }

    private Fixture newFixture() {
        DemandeVignetteMotoRepository repository = mock(DemandeVignetteMotoRepository.class);
        AuthService authService = mock(AuthService.class);
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        // Sommes non stubbees explicitement -> null par defaut (Mockito), gere par ComparaisonPeriode
        // comme un zero (§14 "donnees nulles"). Listes non stubbees -> vides par defaut (Mockito).
        return new Fixture(new VignetteKpiService(repository, authService), repository);
    }

    @Test
    void getKpis_periodeSansDonnees_renvoieDesZerosSansErreur() {
        Fixture fx = newFixture();

        DashboardVignetteResponse response = fx.service().getKpis(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31));

        assertEquals(0L, response.kpis().nombreDemandes().valeur());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.kpis().chiffreAffaires().valeur()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.kpis().panierMoyen().valeur()));
        assertEquals(0.0, response.kpis().tauxRejet().valeur());
        assertNull(response.kpis().delaiMoyenTraitementJours());
        assertEquals(0, response.repartitions().parStatut().size());
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
        DemandeVignetteMotoRepository repository = mock(DemandeVignetteMotoRepository.class);
        AuthService authService = mock(AuthService.class);
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(2L, "user", "USER"));
        VignetteKpiService service = new VignetteKpiService(repository, authService);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getKpis_nombreDemandesEtRepartitionTypeDemande_reprisTelsQuels() {
        Fixture fx = newFixture();
        LocalDate debut = LocalDate.of(2026, 9, 1);
        LocalDate fin = LocalDate.of(2026, 9, 11);

        when(fx.repository().countByCreatedAtBetween(any(), any())).thenReturn(10L);
        when(fx.repository().countByCreatedAtBetweenAndTypeDemande(any(), any(), eq(TypeDemandeVignette.NOUVELLE_DEMANDE))).thenReturn(7L);
        when(fx.repository().countByCreatedAtBetweenAndTypeDemande(any(), any(), eq(TypeDemandeVignette.RENOUVELLEMENT))).thenReturn(3L);

        DashboardVignetteResponse response = fx.service().getKpis(debut, fin);

        assertEquals(10L, response.kpis().nombreDemandes().valeur());
        assertEquals(7L, response.kpis().nouvellesDemandes().valeur());
        assertEquals(3L, response.kpis().renouvellements().valeur());
    }

    @Test
    void getKpis_fraisAccessoires_estSommeDirectementReprise() {
        Fixture fx = newFixture();
        when(fx.repository().sumFraisAccessoires(any(), any())).thenReturn(BigDecimal.valueOf(9500));

        DashboardVignetteResponse response = fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11));

        assertEquals(0, BigDecimal.valueOf(9500).compareTo(response.kpis().totalFraisAccessoires().valeur()));
    }

    @Test
    void getKpis_panierMoyen_chiffreAffairesDiviseParNombreDemandes() {
        Fixture fx = newFixture();
        when(fx.repository().countByCreatedAtBetween(any(), any())).thenReturn(3L);
        when(fx.repository().sumMontantTotal(any(), any())).thenReturn(BigDecimal.valueOf(20750));

        DashboardVignetteResponse response = fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11));

        assertEquals(0, BigDecimal.valueOf(6916.67).compareTo(response.kpis().panierMoyen().valeur()));
    }

    @Test
    void getKpis_tauxLivraisonEtTauxRejet_calculesSelonLesFormulesDonnees() {
        Fixture fx = newFixture();
        when(fx.repository().countByCreatedAtBetween(any(), any())).thenReturn(20L);
        when(fx.repository().countByCreatedAtBetweenAndStatut(any(), any(), eq(StatutDemandeVignetteMoto.LIVREE))).thenReturn(4L);
        when(fx.repository().countByCreatedAtBetweenAndStatut(any(), any(), eq(StatutDemandeVignetteMoto.REJETEE))).thenReturn(2L);
        // "editees ou apres" (surensemble incluant LIVREE, cf. javadoc du service) : 5 -> tauxLivraison = 4/5*100
        when(fx.repository().countByCreatedAtBetweenAndStatutIn(any(), any(), argThatContainsEditee())).thenReturn(5L);

        DashboardVignetteResponse response = fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11));

        assertEquals(80.0, response.kpis().tauxLivraison().valeur());
        assertEquals(10.0, response.kpis().tauxRejet().valeur());
    }

    @Test
    void getKpis_comparaisonPeriodePrecedente_appelleLeRepositoryAvecLesDeuxFenetres() {
        Fixture fx = newFixture();
        LocalDate debut = LocalDate.of(2026, 9, 1);
        LocalDate fin = LocalDate.of(2026, 9, 30);
        // Periode courante : 10 ; periode precedente (aout) : 6.
        when(fx.repository().countByCreatedAtBetween(any(Instant.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    Instant argDebut = invocation.getArgument(0);
                    // La periode courante commence en septembre, la precedente en aout.
                    return argDebut.toString().startsWith("2026-09") ? 10L : 6L;
                });

        DashboardVignetteResponse response = fx.service().getKpis(debut, fin);

        assertEquals(10L, response.kpis().nombreDemandes().valeur());
        assertEquals(6L, response.kpis().nombreDemandes().valeurPrecedente());
        assertEquals(4L, response.kpis().nombreDemandes().variation());
    }

    @Test
    void getKpis_repartitionParStatut_parseCorrectementLenumDepuisLeLibelle() {
        Fixture fx = newFixture();
        LibelleCount libelleCount = mock(LibelleCount.class);
        when(libelleCount.getLibelle()).thenReturn("BROUILLON");
        when(libelleCount.getTotal()).thenReturn(2L);
        when(fx.repository().repartitionParStatut(any(), any())).thenReturn(List.of(libelleCount));

        DashboardVignetteResponse response = fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11));

        assertEquals(1, response.repartitions().parStatut().size());
        assertEquals(StatutDemandeVignetteMoto.BROUILLON, response.repartitions().parStatut().get(0).statut());
        assertEquals(2L, response.repartitions().parStatut().get(0).nombre());
    }

    @Test
    void getKpis_periodeCourteJusqua31Jours_choisitLaGranulariteJour() {
        Fixture fx = newFixture();
        fx.service().getKpis(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));
        org.mockito.Mockito.verify(fx.repository()).evolutionParJour(any(), any());
        org.mockito.Mockito.verify(fx.repository(), org.mockito.Mockito.never()).evolutionParMois(any(), any());
    }

    @Test
    void getKpis_periodeLonguePlusDe6Mois_choisitLaGranulariteMois() {
        Fixture fx = newFixture();
        fx.service().getKpis(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        org.mockito.Mockito.verify(fx.repository()).evolutionParMois(any(), any());
        org.mockito.Mockito.verify(fx.repository(), org.mockito.Mockito.never()).evolutionParJour(any(), any());
    }

    private java.util.Collection<StatutDemandeVignetteMoto> argThatContainsEditee() {
        return org.mockito.ArgumentMatchers.argThat(c -> c != null && c.contains(StatutDemandeVignetteMoto.EDITEE));
    }
}
